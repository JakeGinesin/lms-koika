package lms.koika.demos

import lms.koika.frontend.NanoRisc._

object NanoRiscDemos {
  val r0: Reg = Reg(0)
  val r1: Reg = Reg(1)
  val r2: Reg = Reg(2)
  val r3: Reg = Reg(3)
  val r4: Reg = Reg(4)
  val r5: Reg = Reg(5)
  val r6: Reg = Reg(6)
  val r7: Reg = Reg(7)

  /* mov r2, #0
   * mov r3, #secret_offset
   * mov r4, #0
   *
   * loop:
   * bge r4, #password_size, right
   * ldr r0, [r2, r4]
   * ldr r1, [r3, r4]
   * bne r0, r1, wrong
   * add r4, r4, #1
   * b loop
   *
   * wrong:
   * mov r0, #0
   * b done
   *
   * right:
   * mov r0, #1
   *
   * done:
   *
   * Standard short-circuiting password-checker loop, leaks whether some
   * prefix of the guess is correct.
   *
   * All drivers should detect a timing leak (CBMC should fail).
   */
  def build_shortcircuit_demo(secret_offset: Int, password_size: Int): Vector[Instr] =
    Vector(
      Mov(r2,Imm(0)),
      Mov(r3,Imm(secret_offset)),
      Mov(r4,Imm(0)),
      B(Some((Ge,r4,Imm(password_size))),Addr(11)),
      Load(r0,r2,r4),
      Load(r1,r3,r4),
      B(Some((Ne,r0,r1)),Addr(9)),
      Binop(Plus,r4,r4,Imm(1)),
      B(None,Addr(3)),
      Mov(r0,Imm(0)),
      B(None,Addr(12)),
      Mov(r0,Imm(1)),
    )

  /* beq r0, #0, done
   * ldr r1, [r0]
   * ldr r2, [r1, #4]
   *
   * done:
   *
   * More minimal version of the SPECTRE demo below. Initially used for
   * debugging, kept as regression test.
   *
   * Naive/Cache: CBMC passes (fail to detect)
   * Speculative: CBMC fails (leak detected)
   */
  def spec_small: Vector[Instr] =
    Vector(B(Some((Eq,Reg(0),Imm(0))),Addr(3)),
           Load(r1,r0,Imm(0)),
           Load(r2,r1,Imm(4)))

  /* mov r3, #0
   * mov r0, #secret_offset
   * bge r0, #secret_offset, done
   * ldr r1, [r3, r0]
   * ldr r2, [r1, #0]
   *
   * done:
   *
   * SPECTRE vulnerability.
   *
   * Naive/Cache: CBMC passes (fail to detect)
   * Speculative: CBMC fails (leak detected)
   */
  def build_spectre_demo(secret_offset: Int): Vector[Instr] =
    Vector(
      Mov(r3,Imm(0)),
      Mov(r0,Imm(secret_offset)),
      B(Some(Ge,r0,Imm(secret_offset)),Addr(5)),
      Load(r1,r3,r0),
      Load(r2,r1,Imm(0))
    )
}
