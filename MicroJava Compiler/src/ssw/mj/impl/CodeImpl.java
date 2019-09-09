package ssw.mj.impl;

import ssw.mj.Errors.Message;
import ssw.mj.Parser;
import ssw.mj.codegen.Code;
import ssw.mj.codegen.Operand;
import ssw.mj.symtab.Tab;

public final class CodeImpl extends Code {

	public CodeImpl(Parser p) {
		super(p);
	}

	// TODO Exercise 5 - 6: implementation of code generation

	// load operations --------
	void load(Operand x) {
		switch (x.kind) {
		case Con:
			loadConst(x);
			break;
		case Local:
			loadLocal(x);
			break;
		case Static:
			loadStatic(x);
			break;
		case Stack:
			break;
		case Fld:
			loadField(x);
			break;
		case Elem:
			loadElem(x);
			break;
		default:
			parser.error(Message.NO_VAL);
		}
		x.kind = Operand.Kind.Stack;
	}

	private void loadElem(Operand x) {
		if (x.type == Tab.charType) {
			put(OpCode.baload);
		} else {
			put(OpCode.aload);
		}
	}

	private void loadField(Operand x) {
		put(OpCode.getfield);
		put2(x.adr);
	}

	private void loadStatic(Operand x) {
		put(OpCode.getstatic);
		put2(x.adr);
	}

	private void loadLocal(Operand x) {
		switch (x.adr) {
		case 0:
			put(OpCode.load_0);
			break;
		case 1:
			put(OpCode.load_1);
			break;
		case 2:
			put(OpCode.load_2);
			break;
		case 3:
			put(OpCode.load_3);
			break;
		default:
			put(OpCode.load);
			put(x.adr);
			break;
		}
	}

	private void loadConst(Operand x) {
		switch (x.val) {
		case -1:
			put(OpCode.const_m1);
			break;
		case 0:
			put(OpCode.const_0);
			break;
		case 1:
			put(OpCode.const_1);
			break;
		case 2:
			put(OpCode.const_2);
			break;
		case 3:
			put(OpCode.const_3);
			break;
		case 4:
			put(OpCode.const_4);
			break;
		case 5:
			put(OpCode.const_5);
			break;
		default:
			put(OpCode.const_);
			put4(x.val);
		}

	}

	// store operations ----------

	public void store(Operand x) {
		switch (x.kind) {
		case Static:
			storeStatic(x);
			break;

		case Local:
			storeLocal(x);
			break;
		case Fld:
			storeField(x);
			break;
		case Elem:
			storeElem(x);
			break;
		default:
			parser.error(Message.NO_VAR);
		}
	}

	private void storeStatic(Operand x) {
		put(OpCode.putstatic);
		put2(x.adr);
	}

	private void storeLocal(Operand x) {
		switch (x.adr) {
		case 0:
			put(OpCode.store_0);
			break;
		case 1:
			put(OpCode.store_1);
			break;
		case 2:
			put(OpCode.store_2);
			break;
		case 3:
			put(OpCode.store_3);
			break;
		default:
			put(OpCode.store);
			put(x.adr);
		}
	}

	private void storeField(Operand x) {
		put(OpCode.putfield);
		put2(x.adr);
	}

	private void storeElem(Operand x) {
		put((x.type == Tab.charType) ? OpCode.bastore : OpCode.astore);
	}

	// ------

	void assign(Operand x, Operand y) {
		load(y);
		store(x);
	}

	public void increment(Operand x, int value) {
		switch (x.kind) {
		case Static:
			loadStatic(x);
			load(new Operand(value));
			put(OpCode.add);
			storeStatic(x);
			break;
		case Local:
			put(OpCode.inc);
			put(x.adr);
			put(value);
			break;
		case Fld:
			put(OpCode.dup);
			loadField(x);
			load(new Operand(value));
			put(OpCode.add);
			storeField(x);
			break;
		case Elem:
			put(OpCode.dup2);
			loadElem(x);
			load(new Operand(value));
			put(OpCode.add);
			storeElem(x);
			break;

		default:
			parser.error(Message.NO_VAR);
		}
	}

	/**
	 * Generates an unconditional jump instruction to a label.
	 * 
	 * @param label
	 *            the label
	 */
	public void jump(LabelImpl label) {
		put(OpCode.jmp);
		label.putAdr();
	}

	/**
	 * Generates a conditional jump instruction for a true jump
	 * 
	 * @param x
	 *            the condition operand
	 */
	public void tJump(Operand x) {
		put(getCode(x.op));

		x.tLabel.putAdr();
	}

	/**
	 * Generates a conditional jump instruction for a false jump
	 * 
	 * @param x
	 *            the condition operand
	 */
	public void fJump(Operand x) {
		put(getCode(CompOp.invert(x.op)));
		x.fLabel.putAdr();
	}

	private OpCode getCode(CompOp op) {
		switch (op) {
		case eq:
			return OpCode.jeq;
		case ne:
			return OpCode.jne;
		case gt:
			return OpCode.jgt;
		case ge:
			return OpCode.jge;
		case lt:
			return OpCode.jlt;
		case le:
			return OpCode.jle;
		default:
			return OpCode.nop;
		}
	}

}
