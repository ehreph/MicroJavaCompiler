package ssw.mj.impl;

import ssw.mj.Errors.Message;
import ssw.mj.Parser;
import ssw.mj.Scanner;
import ssw.mj.Token;
import ssw.mj.Token.Kind;
import ssw.mj.codegen.Code.CompOp;
import ssw.mj.codegen.Code.OpCode;
import ssw.mj.codegen.Code;
import ssw.mj.codegen.Operand;
import ssw.mj.symtab.Obj;
import ssw.mj.symtab.Struct;
import ssw.mj.symtab.Tab;

import static ssw.mj.Errors.Message.*;
import static ssw.mj.Token.Kind.*;

import java.util.*;
import java.util.Map.Entry;

public final class ParserImpl extends Parser {

	// TODO Exercise 3 - 6: implementation of parser

	private static final String MAIN_METHOD_NAME = "main";

	private static final int MIN_ERROR_DISTANCE = 3;

	private int errorDistance;
	private boolean mainExists = false;

	private final HashSet<Kind> followStatment = new HashSet<>(Arrays.asList(
			eof, semicolon, if_, while_, break_, return_, read, print, rbrace));
	private final HashSet<Kind> followMethod = new HashSet<>(
			Arrays.asList(eof, void_, rbrace));
	private final HashSet<Kind> followDecl = new HashSet<>(
			Arrays.asList(lbrace, final_, class_, ident, eof));

	private final HashSet<Kind> firstStatement = new HashSet<>(
			Arrays.asList(lbrace, ident, if_, while_, break_, continue_,
					return_, read, print, semicolon));
	private final HashSet<Kind> firstMethod = new HashSet<>(
			Arrays.asList(ident, void_));
	private final HashSet<Kind> firstAssignop = new HashSet<>(
			Arrays.asList(assign, plusas, minusas, timesas, slashas, remas));
	private final HashSet<Kind> firstMulop = new HashSet<>(
			Arrays.asList(times, slash, rem));
	private final HashSet<Kind> firstExpr = new HashSet<>(
			Arrays.asList(ident, number, charConst, new_, lpar, minus));

	private Obj curMethod;

	private LabelImpl breakLabel;
	private LabelImpl continueLabel;

	private final Deque<LabelImpl> breaks;
	private final Deque<LabelImpl> continues;

	public ParserImpl(Scanner scanner) {
		super(scanner);
		errorDistance = MIN_ERROR_DISTANCE;
		this.breaks = new ArrayDeque<>();
		this.continues = new ArrayDeque<>();

		// Avoid crash when 1st symbol has scanner error.
		this.la = new Token(Kind.none, 1, 1);
	}

	@Override
	public void parse() {
		scan();
		program();
		check(eof);
	}

	private void scan() {
		t = la;
		la = scanner.next();
		sym = la.kind;
		errorDistance++;
	}

	private void check(Kind expected) {
		if (sym == expected) {
			scan();
		} else {
			error(TOKEN_EXPECTED, expected);
		}
	}

	private void program() {
		check(program);
		check(ident);
		Obj prog = tab.insert(Obj.Kind.Prog, t.str,
				new StructImpl(Struct.Kind.None));
		tab.openScope();
		while (true) {
			if (!followDecl.contains(sym)) {
				recoverDecl();
			} else if (sym == final_) {
				ConstDecl();
			} else if (sym == ident) {
				VarDecl();
			} else if (sym == class_) {
				ClassDecl();
			} else {
				break;
			}
		}
		hasTooManyGlobals();

		check(lbrace);
		while (sym != rbrace && sym != eof) {
			if (firstMethod.contains(sym)) {
				MethodDecl();
			} else if (followMethod.contains(sym)) {
				break;
			} else {
				recoverMeth();
			}
		}
		if (!mainExists) {
			error(Message.METH_NOT_FOUND, MAIN_METHOD_NAME);
		}

		check(Kind.rbrace);
		prog.locals = tab.curScope.locals();
		code.dataSize = tab.curScope.nVars();
		tab.closeScope();

	}

	private void ConstDecl() {
		check(final_);
		StructImpl type = Type();
		check(ident);
		String name = t.str;
		check(assign);

		if (sym != number && sym != charConst) {
			error(CONST_DECL);
		}

		Kind kind = (type.kind.equals(Struct.Kind.Int)) ? Kind.number
				: Kind.charConst;

		if (kind.equals(sym)) {
			Obj con = tab.insert(Obj.Kind.Con, name, type);
			scan();
			con.val = t.val;
		} else {
			error(CONST_TYPE);
			scan();
		}
		check(semicolon);
	}

	private void VarDecl() {
		StructImpl type = Type();
		check(Kind.ident);
		if (type != Tab.noType) {
			tab.insert(Obj.Kind.Var, t.str, type);
		}

		while (sym == comma) {
			scan();
			check(ident);
			tab.insert(Obj.Kind.Var, t.str, type);
		}
		check(Kind.semicolon);
	}

	private void ClassDecl() {
		check(class_);
		check(ident);
		Obj clazz = tab.insert(Obj.Kind.Type, t.str,
				new StructImpl(Struct.Kind.Class));
		check(lbrace);
		tab.openScope();

		while (sym == ident) {
			VarDecl();
		}
		if (tab.curScope.nVars() > MAX_FIELDS) {
			error(TOO_MANY_FIELDS);
		}

		clazz.type.fields = tab.curScope.locals();
		check(rbrace);
		tab.closeScope();
	}

	private void MethodDecl() {
		StructImpl type = Tab.noType;

		if (sym == ident) {
			type = Type();
		} else if (sym == void_) {
			scan();
		} else {
			error(METH_DECL);
		}

		check(ident);
		String methodName = t.str;

		curMethod = tab.insert(Obj.Kind.Meth, methodName, type);
		curMethod.adr = code.pc;
		tab.openScope();
		check(lpar);
		if (sym == ident) {
			curMethod.nPars = FormPars();
		}
		check(rpar);

		if (curMethod != null) {
			curMethod.nPars = tab.curScope.nVars();
			if (MAIN_METHOD_NAME.equals(curMethod.name)) {
				mainExists = true;
				code.mainpc = code.pc;
				if (tab.curScope.nVars() > 0) {
					error(MAIN_WITH_PARAMS);
				} else if (!curMethod.type.kind.equals(Struct.Kind.None)) {
					error(MAIN_NOT_VOID);
				}
			}
		}

		while (sym == ident) {
			VarDecl();
		}
		if (tab.curScope.nVars() > MAX_LOCALS) {
			error(TOO_MANY_LOCALS);
		}
		if (curMethod != null) {
			curMethod.locals = tab.curScope.locals();
			code.put(Code.OpCode.enter);
			code.put(curMethod.nPars);
			code.put(tab.curScope.nVars());
		}

		Block();

		if (curMethod.type == Tab.noType) {
			code.put(Code.OpCode.exit);
			code.put(Code.OpCode.return_);
		} else {
			code.put(Code.OpCode.trap);
			code.put(1);
		}
		tab.closeScope();

	}

	private int FormPars() {
		int nPars = 0;

		StructImpl type = Type();
		check(ident);
		tab.insert(Obj.Kind.Var, t.str, type);
		nPars++;

		while (sym == comma) {
			scan();
			type = Type();
			check(ident);
			tab.insert(Obj.Kind.Var, t.str, type);
			nPars++;
		}

		return nPars;

	}

	private StructImpl Type() {

		check(ident);
		Obj o = tab.find(t.str);
		if (o.kind != Obj.Kind.Type) {
			error(NO_TYPE);
		}
		StructImpl type = o.type;
		if (sym == lbrack) {
			scan();
			check(rbrack);
			type = new StructImpl(type);
		}
		return type;

	}

	private void Block() {
		check(lbrace);
		while (sym != rbrace) {
			if (firstStatement.contains(sym)) {
				Statement();
			} else if (followStatment.contains(sym)) {
				break;
			} else {
				recoverStat();
			}
		}
		check(rbrace);
	}

	private void Statement() {
		switch (sym) {
		case ident:
			statementIdent();
			break;
		case if_:
			statementIfElse();
			break;
		case while_:
			statementWhile();
			break;
		case break_:
			statementBreak();
			break;
		case continue_:
			statementContinue();
			break;
		case return_:
			statementReturn();
			break;
		case read:
			statementRead();
			break;
		case print:
			statementPrint();
			break;
		case lbrace:
			Block();
			break;
		case semicolon:
			scan();
			break;
		default:
			error(INVALID_STAT);
			break;
		}

	}

	private void statementPrint() {
		Operand x;
		scan();
		check(Kind.lpar);
		x = Expr();

		int val = 0;

		if (sym == comma) {
			scan();
			check(number);
			val = t.val;
		}

		code.load(x);
		code.load(new Operand(val));

		if (x.type == Tab.intType) {
			code.put(OpCode.print);
		} else if (x.type == Tab.charType) {
			code.put(OpCode.bprint);
		} else {
			error(Message.PRINT_VALUE);
		}

		check(rpar);
		check(semicolon);
	}

	private void statementRead() {
		Operand x;
		scan();
		check(lpar);
		check(ident);
		x = Designator();

		if (x.type == Tab.intType) {
			code.put(OpCode.read);
		} else if (x.type == Tab.charType) {
			code.put(OpCode.bread);
		} else {
			error(Message.READ_VALUE);
		}

		code.store(x);

		check(Kind.rpar);
		check(Kind.semicolon);
	}

	private void statementReturn() {
		Operand x;
		scan();
		if (firstExpr.contains(sym)) {
			if (curMethod.type == Tab.noType) {
				error(Message.RETURN_VOID);
			}

			x = Expr();
			code.load(x);

			if (!x.type.assignableTo(curMethod.type)) {
				error(Message.RETURN_TYPE);
			}
		} else {
			if (curMethod.type != Tab.noType) {
				error(Message.RETURN_NO_VAL);
			}
		}
		code.put(OpCode.exit);
		code.put(OpCode.return_);
		check(semicolon);
	}

	private void statementContinue() {
		scan();
		if (continueLabel == null) {
			error(Message.NO_LOOP_CONTINUE);
		} else {
			code.jump(continueLabel);
		}
		check(semicolon);
	}

	private void statementBreak() {
		scan();
		if (breakLabel == null) {
			error(Message.NO_LOOP_BREAK);
		} else {
			code.jump(breakLabel);
		}
		check(semicolon);
	}

	private void statementWhile() {
		Operand x;
		scan();
		check(lpar);

		LabelImpl top = new LabelImpl(code);
		top.here();
		boolean contPushed = false;
		if (continueLabel != null) {
			continues.push(continueLabel);
			contPushed = true;
		}
		continueLabel = new LabelImpl(code);
		continueLabel.here();

		boolean pushed = false;
		if (breakLabel != null) {
			breaks.push(breakLabel);
			pushed = true;
		}
		breakLabel = new LabelImpl(code);
		x = Condition();

		if (x.op != null) {
			code.fJump(x);
		}
		x.tLabel.here();

		check(rpar);
		Statement();

		code.jump(top);
		x.fLabel.here();
		breakLabel.here();

		if (pushed) {
			breakLabel = breaks.pop();
		}
		if (contPushed) {
			continueLabel = continues.pop();
		}
	}

	private void statementIdent() {
		Operand x;
		Operand y;
		scan();
		x = Designator();

		if (firstAssignop.contains(sym)) {
			OpCode assignOp = Assignop();
			Operand.Kind k = x.kind;

			if (assignOp != Code.OpCode.nop) {

				if (k == Operand.Kind.Stack || k == Operand.Kind.Meth
						|| k == Operand.Kind.Cond) {
					error(Message.NO_VAR);
				}

				if (k == Operand.Kind.Local || k == Operand.Kind.Static) {
					code.load(x);
				} else {
					if (k == Operand.Kind.Elem) {
						code.put(OpCode.dup2);
					} else if (k == Operand.Kind.Fld) {
						code.put(OpCode.dup);
					}
					x.kind = k;
					code.load(x);
				}
				y = Expr();

				if (x.type == Tab.intType && y.type == Tab.intType) {
					code.load(y);
					code.put(assignOp);
				} else {
					error(Message.NO_INT_OP);
				}
			} else {
				y = Expr();
				if (y.kind == Operand.Kind.Meth) {
					y.kind = Operand.Kind.Stack;
				} else {
					code.load(y);
				}

			}
			if (y.type.assignableTo(x.type)) {
				x.kind = k;
				code.assign(x, y);
			} else {
				error(Message.INCOMP_TYPES);
			}
		} else if (sym == lpar) {
			ActPars(x);
		} else if (sym == pplus || sym == mminus) {
			if (x.type != Tab.intType) {
				error(NO_INT);
			}
			code.increment(x, (sym == Kind.pplus) ? 1 : -1);
			scan();

		} else {
			error(DESIGN_FOLLOW);
		}
		check(semicolon);
	}

	private void statementIfElse() {
		Operand x;
		scan();
		check(lpar);
		x = Condition();

		if (x.op != null) {
			code.fJump(x);
		}
		x.tLabel.here();

		check(rpar);
		Statement();
		if (sym == else_) {
			scan();
			LabelImpl end = new LabelImpl(code);
			code.jump(end);
			x.fLabel.here();
			Statement();
			end.here();
		} else {
			x.fLabel.here();
		}
	}

	private OpCode Assignop() {
		OpCode opCode = OpCode.nop;
		switch (sym) {
		case assign:
			scan();
			opCode = OpCode.nop;
			break;
		case plusas:
			scan();
			opCode = OpCode.add;
			break;
		case minusas:
			scan();
			opCode = OpCode.sub;
			break;
		case timesas:
			scan();
			opCode = OpCode.mul;
			break;
		case slashas:
			scan();
			opCode = OpCode.div;
			break;
		case remas:
			scan();
			opCode = OpCode.rem;
			break;
		default:
			error(ASSIGN_OP);
		}
		return opCode;
	}

	private void ActPars(Operand op) {
		check(lpar);

		if (op.kind != Operand.Kind.Meth) {
			error(Message.NO_METH);
			op.obj = tab.noObj;
		}

		int aPars = 0;
		int fPars = op.obj.nPars;

		Iterator<Entry<String, Obj>> locals = op.obj.locals.entrySet()
				.iterator();

		if (firstExpr.contains(sym)) {
			Operand exOp = Expr();
			code.load(exOp);
			aPars++;
			if (op.kind == Operand.Kind.Meth && aPars <= fPars) {
				Obj fp = locals.next().getValue();

				if (!exOp.type.assignableTo(fp.type)) {
					error(Message.PARAM_TYPE);
				}
			}
			while (sym == comma) {
				scan();
				exOp = Expr();
				code.load(exOp);
				aPars++;
				if (op.kind == Operand.Kind.Meth && aPars <= fPars) {
					Obj fp = locals.next().getValue();

					if (!exOp.type.assignableTo(fp.type)) {
						error(Message.PARAM_TYPE);
					}
				}
			}
		}

		if (op.obj != null) {
			if (aPars > fPars) {
				error(Message.MORE_ACTUAL_PARAMS);
			} else if (aPars < fPars) {
				error(Message.LESS_ACTUAL_PARAMS);
			}

			if (op.obj == tab.chrObj || op.obj == tab.ordObj) {
				// nothing todo in this case
			} else if (op.obj == tab.lenObj) {
				code.put(OpCode.arraylength);
			} else {
				code.put(OpCode.call);
				code.put2(op.adr - (code.pc - 1));
			}
			op.kind = Operand.Kind.Stack;
		}

		check(rpar);
	}

	private Operand Condition() {
		Operand x = CondTerm();
		while (sym == or) {
			code.tJump(x);
			scan();
			x.fLabel.here();

			Operand y = CondTerm();
			x.fLabel = y.fLabel;
			x.op = y.op;
		}
		return x;
	}

	private Operand CondTerm() {
		Operand x = CondFact();
		while (sym == and) {
			code.fJump(x);
			scan();
			Operand y = CondFact();
			x.op = y.op;
		}
		return x;
	}

	private Operand CondFact() {

		Operand x = Expr();
		code.load(x);

		CompOp op = Relop();

		Operand y = Expr();
		code.load(y);
		if (!x.type.compatibleWith(y.type)) {
			error(Message.INCOMP_TYPES);
		}
		if (x.type.isRefType() && op != null && op != CompOp.eq
				&& op != CompOp.ne) {
			error(Message.EQ_CHECK);
		}
		return new Operand(op, code);
	}

	private CompOp Relop() {
		CompOp op = null;
		switch (sym) {
		case eql:
			scan();
			op = CompOp.eq;
			break;
		case neq:
			scan();
			op = CompOp.ne;
			break;
		case gtr:
			scan();
			op = CompOp.gt;
			break;
		case geq:
			scan();
			op = CompOp.ge;
			break;
		case lss:
			scan();
			op = CompOp.lt;
			break;
		case leq:
			scan();
			op = CompOp.le;
			break;
		default:
			error(Message.REL_OP);
		}
		return op;
	}

	private Operand Expr() {

		boolean negative = false;

		if (sym == minus) {
			scan();
			negative = true;
		}
		Operand x = Term();

		if (negative) {
			if (x.type != Tab.intType) {
				error(Message.NO_INT_OP);
			} else {
				if (x.kind == Operand.Kind.Con) {
					x.val = -x.val;
				} else {
					code.load(x);
					code.put(OpCode.neg);
				}
			}
		}

		while (sym == plus || sym == minus) {
			OpCode addOp = Addop();
			code.load(x);
			Operand y = Term();
			code.load(y);

			if (x.type != Tab.intType || y.type != Tab.intType) {
				error(Message.NO_INT_OP);
			}

			code.put(addOp);
		}
		return x;
	}

	private Operand Term() {
		Operand x = Factor();
		while (firstMulop.contains(sym)) {
			OpCode mulop = Mulop();
			code.load(x);
			Operand y = Factor();
			code.load(y);

			if (x.type != Tab.intType || y.type != Tab.intType) {
				error(Message.NO_INT_OP);
			}
			code.put(mulop);
		}

		return x;
	}

	private Operand Factor() {
		Operand op = null;
		switch (sym) {
		case ident:
			scan();
			op = Designator();
			if (op.kind == Operand.Kind.Meth && op.type == Tab.noType) {
				error(INVALID_CALL);
			}

			if (sym == lpar) {
				ActPars(op);
				if (op.type == Tab.noType) {
					error(Message.NO_TYPE);
				}
			} else {
				if (op.kind == Operand.Kind.Meth) {
					error(Message.NO_VAL);
				}
			}

			break;
		case number:
			scan();
			op = new Operand(t.val);
			break;
		case charConst:
			scan();
			op = new Operand(t.val);
			op.type = Tab.charType;
			break;
		case new_:
			scan();
			check(ident);

			Obj obj = tab.find(t.str);
			if (obj.type == Tab.noType) {
				error(NO_TYPE);
			}

			StructImpl type = obj.type;

			if (sym == lbrack) {
				scan();

				if (obj.kind != Obj.Kind.Type) {
					error(Message.NO_TYPE);
				}

				op = Expr();

				if (op.type != Tab.intType) {
					error(Message.ARRAY_SIZE);
				}

				check(rbrack);
				code.load(op);
				code.put(OpCode.newarray);

				if (type == Tab.charType) {
					code.put(0);
				} else {
					code.put(1);
				}
				type = new StructImpl(type);
			} else {
				if (obj.kind != Obj.Kind.Type
						|| type.kind != Struct.Kind.Class) {
					error(Message.NO_CLASS_TYPE);
				}
				code.put(OpCode.new_);
				code.put2(type.nrFields());
			}

			op = new Operand(0);
			op.kind = Operand.Kind.Stack;
			op.type = type;

			break;
		case lpar:
			scan();
			op = Expr();
			check(rpar);
			break;
		default:
			error(INVALID_FACT);
			op = new Operand(Tab.noType);
			break;
		}

		return op;

	}

	private Operand Designator() {
		Operand x = new Operand(tab.find(t.str), this);
		while (sym == period || sym == lbrack) {
			if (sym == period) {

				if (x.type.kind != Struct.Kind.Class) {
					error(Message.NO_CLASS);
				}
				code.load(x);
				scan();
				check(ident);
				Obj obj = tab.findField(t.str, x.type);
				x.kind = Operand.Kind.Fld;
				x.type = obj.type;
				x.adr = obj.adr;
			} else {
				code.load(x);
				scan();
				Operand y = Expr();

				if (x.type.kind != Struct.Kind.Arr) {
					error(Message.NO_ARRAY);
				}

				if (y.type.kind != Struct.Kind.Int) {
					error(ARRAY_INDEX);
				}

				code.load(y);
				x.kind = Operand.Kind.Elem;
				x.type = x.type.elemType;
				check(rbrack);
			}
		}
		return x;
	}

	private OpCode Addop() {
		OpCode op = null;
		if (sym == plus) {
			scan();
			op = OpCode.add;
		} else if (sym == minus) {
			scan();
			op = OpCode.sub;
		} else {
			error(ADD_OP);
		}
		return op;
	}

	private OpCode Mulop() {
		OpCode op = null;
		switch (sym) {
		case times:
			scan();
			op = OpCode.mul;
			break;
		case slash:
			scan();
			op = OpCode.div;
			break;
		case rem:
			scan();
			op = OpCode.rem;
			break;
		default:
			error(MUL_OP);
		}
		return op;
	}

	private void hasTooManyGlobals() {
		if (tab.curScope.nVars() > MAX_GLOBALS) {
			error(TOO_MANY_GLOBALS);
		}
	}

	/**
	 * scans symbols until it follows global declarations or eof
	 */
	private void recoverDecl() {

		error(Message.INVALID_DECL);
		while (true) {
			Obj obj = null;
			scan();
			if (sym == ident) {
				 obj = tab.find(t.str);
			}
			if (followDecl.contains(sym)
					|| (obj != null && obj.kind == Obj.Kind.Type)) {
				break;
			}
		}
	}

	private void recoverMeth() {
		error(METH_DECL);
		while ((sym == ident && !tab.find(la.str).kind.equals(Obj.Kind.Type))
				|| !followMethod.contains(sym)) {
			scan();
		}
		if (sym == Kind.rbrace && sym != eof) {
			scan();
		}
		errorDistance = 0;
	}

	private void recoverStat() {
		error(INVALID_STAT);
		while (!followStatment.contains(sym)) {
			scan();
		}
		errorDistance = 0;
	}

	@Override
	public void error(Message msg, Object... msgParams) {
		if (errorDistance >= 3) {
			scanner.errors.error(la.line, la.col, msg, msgParams);
		}
		errorDistance = 0;
	}
}