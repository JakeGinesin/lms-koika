package lms.koika.demos

import lms.core.stub._
import lms.core.virtualize
import lms.macros.SourceContext
import lms.collection.mutable._
import lms.koika.frontend.NanoRisc._
import KoikaInterp._

import lms.koika._
import lms.koika.frontend.NanoRisc._

/**
  * side‑channel model: **instruction cache timing**.
  *
  * * 2‑entry LRU queue (`ICACHE_LRU_SIZE = 2`).
  * * fetching of a PC that is **not** in the queue -> **miss** (+20 cycles)
  * * Subsequent fetches while resident -> **hit** (+1 cycle) and entry promoted to MRU
  *
  * This behaviour leaks which path of control‑flow executed.
  */
trait ICache extends Cached { self =>
  val ICACHE_LRU_SIZE = 2

  private def lookup(s: Rep[StateT], pc: Rep[Int]): Rep[Int] = {
    // returns 0 / 1 on hit, -1 on miss
    __ifThenElse(s.icache_keys(0) == pc, 0,
      __ifThenElse(s.icache_keys(1) == pc, 1, -1))
  }

  private def promoteToMRU(s: Rep[StateT], idx: Rep[Int]): Rep[Unit] = {
    if (idx == unit(1)) {
      val tmp = s.icache_keys(1)
      s.icache_keys(1) = s.icache_keys(0)
      s.icache_keys(0) = tmp
    }
  }

  private def missFill(s: Rep[StateT], pc: Rep[Int]): Rep[Unit] = {
    // shift LRU and install new pc at head
    s.icache_keys(1) = s.icache_keys(0)
    s.icache_keys(0) = pc
    s.timer += 20      // miss penalty
  }

  /** charge timing before executing the instruction at static index `i`. */
  private def accountFetch(s: Rep[StateT], pc: Rep[Int]): Rep[Unit] = {
    val idx = lookup(s, pc)
    if (idx == unit(-1)) {
      missFill(s, pc)
    } else {
      promoteToMRU(s, idx)
      s.timer += 1      // hit cost (+1 for now)
    }
    ()
  }

  /* -------------- hook into trampoline -------------- */
  abstract override def call(i: Int, s: Rep[StateT]): Rep[StateT] = {
    accountFetch(s, unit(i))
    super.call(i, s)
  }
}

// demo programs
object ICacheDemos {
  val r0 = Reg(0)
  val r1 = Reg(1)

  /**
    * divergent control‑flow whose timing now depends on branch outcome.
    *
    *   if r0 == 0  => takes hot path (PCs 1 & 2 are cached after first run)
    *   else         => jumps to cold path at PC 3 → incurs an I‑cache miss
    */
  val branchICacheLeak: Vector[Instr] = Vector(
    /*0*/ B(Some((Eq, r0, Imm(0))), Addr(3)),
    /*1*/ Mov(r1, Imm(42)),        // hot path – will be cached
          B(None, Addr(4)),
    /*3*/ Mov(r1, Imm(99)),        // cold path – different PC
    /*4*/ // done
  )
}

@virtualize
class NanoRiscICacheTests extends TutorialFunSuite {
  import KoikaInterp._

  override def exec(label: String, code: String, suffix: String = "c") =
    super.exec(label, code, suffix)

  override def check(label: String, code: String, suffix: String = "c") =
    super.check(label, code, suffix)

  val under = "demos/icache_"

  trait Driver extends GenericKoikaDriver[StateT, StateT] with ICache {
    override val init = s"""
#define CACHE_LRU_SIZE 2
#define ICACHE_LRU_SIZE 2
void init(struct $stateT *s) {
  s->regs = calloc(sizeof(int), NUM_REGS);
  s->saved_regs = calloc(sizeof(int), NUM_REGS);
  for (int i=0;i<NUM_REGS;i++){ s->regs[i]=0; s->saved_regs[i]=0; }
  s->timer = 0;
  s->mem = calloc(sizeof(int), MEM_SIZE);
  for (int i=0;i<MEM_SIZE;i++){ s->mem[i]=0; }
  s->cache_keys = calloc(sizeof(int), CACHE_LRU_SIZE);
  s->cache_vals = calloc(sizeof(int), CACHE_LRU_SIZE);
  for (int i=0;i<CACHE_LRU_SIZE;i++){ s->cache_keys[i] = -1; s->cache_vals[i] = 0; }
  /* new instruction cache */
  s->icache_keys = calloc(sizeof(int), ICACHE_LRU_SIZE);
  for (int i=0;i<ICACHE_LRU_SIZE;i++){ s->icache_keys[i] = -1; }
}"""
  }

  test("icache side‑channel leak") {
    val snippet = new Driver {
      override val prog = ICacheDemos.branchICacheLeak
    }
    check("icache_leak", snippet.code)
  }
}
