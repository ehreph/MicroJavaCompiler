package ssw.mj.impl;

import ssw.mj.Errors.Message;
import ssw.mj.Parser;
import ssw.mj.symtab.Obj;
import java.util.LinkedHashMap;
import ssw.mj.symtab.Scope;
import ssw.mj.symtab.Struct;
import ssw.mj.symtab.Tab;

import static ssw.mj.symtab.Obj.Kind.*;

public final class TabImpl extends Tab {

	static final StructImpl arrType = new StructImpl(noType);

	public TabImpl(Parser p) {
		super(p);
		// universe scope
		curScope = new Scope(null);

		insert(Type, "int", intType);
		insert(Type, "char", charType);
		insert(Con, "null", nullType);

		chrObj = insert(Meth, "chr", charType);
		openScope();
		Obj iObj = insert(Var, "i", intType);
		chrObj.locals = new LinkedHashMap<>();
		chrObj.locals.put(iObj.name, iObj);
		chrObj.nPars++;
		closeScope();

		ordObj = insert(Meth, "ord", intType);
		openScope();
		Obj chObj = insert(Var, "ch", charType);
		ordObj.locals = new LinkedHashMap<>();
		ordObj.locals.put(chObj.name, chObj);
		ordObj.nPars++;
		closeScope();

		lenObj = insert(Meth, "len", intType);
		openScope();
		Obj arrObj = insert(Var, "arr", arrType);
		lenObj.locals = new LinkedHashMap<>();
		lenObj.locals.put(arrObj.name, arrObj);
		lenObj.nPars++;
		closeScope();

		openScope();
		noObj = insert(Var, "noObj", noType);
		closeScope();

		// universe level = -1
		curLevel = -1;
	}

	void openScope() {
		curScope = new Scope(curScope);
		curLevel++;
	}

	void closeScope() {
		curScope = curScope.outer();
		curLevel--;
	}

	Obj insert(Obj.Kind kind, String name, StructImpl type) {
		Obj obj = curScope.findLocal(name);
		if (obj == null) {
			obj = new Obj(kind, name, type);
			if (kind == Obj.Kind.Var) {
				obj.adr = curScope.nVars();
				if (curLevel > 0) {
					obj.level = 1;
				} else {
					obj.level = 0;
				}
			}
		} else {
			parser.error(Message.DECL_NAME, name);
		}
		curScope.insert(obj);
		return obj;
	}

	Obj find(String name) {
		Obj obj = curScope.findGlobal(name);

		if (obj == null) {
			parser.error(Message.NOT_FOUND, name);
			return noObj;
		}
		return obj;
	}

	Obj findField(String name, Struct type) {
		Obj field = type.findField(name);
		if (field == null) {
			parser.error(Message.NO_FIELD, name);
			field = noObj;
		}
		return field;
	}
}
