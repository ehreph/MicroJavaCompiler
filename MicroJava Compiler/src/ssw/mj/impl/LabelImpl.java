package ssw.mj.impl;

import java.util.ArrayList;
import java.util.List;

import ssw.mj.codegen.Code;
import ssw.mj.codegen.Label;

public final class LabelImpl extends Label {

	// TODO Exercise 6: Implementation of Labels for management of jump targets

	/** The label address */
	private int adr;

	/** The code positions to fix */
	private final List<Integer> fixupList;

	public LabelImpl(Code code) {
		super(code);
		this.adr = -1;
		this.fixupList = new ArrayList<>();
	}

	public void putAdr() {
		if (adr >= 0) {
			code.put2(adr - (code.pc - 1));
		} else {
			fixupList.add(code.pc);

			// insert placeholder
			code.put2(0);
		}
	}

	public void here() {
		if (adr >= 0) {
			throw new IllegalStateException("label has been defined twice");
		}

		for (int position : fixupList) {
			code.put2(position, code.pc - (position - 1));
		}

		fixupList.clear();
		adr = code.pc;
	}
}
