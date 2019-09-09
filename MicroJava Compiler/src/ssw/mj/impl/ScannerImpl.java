package ssw.mj.impl;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;

import ssw.mj.Errors.Message;
import ssw.mj.Scanner;
import ssw.mj.Token;
import ssw.mj.Token.Kind;

import static ssw.mj.Token.Kind.*;

public final class ScannerImpl extends Scanner {

	private final HashMap<String, Token.Kind> commands = new HashMap<String, Token.Kind>() {

		private static final long serialVersionUID = 6995472587252510079L;
		{
			put("break", Kind.break_);
			put("continue", Kind.continue_);
			put("class", Kind.class_);
			put("else", Kind.else_);
			put("final", Kind.final_);
			put("if", Kind.if_);
			put("new", Kind.new_);
			put("print", Kind.print);
			put("program", Kind.program);
			put("read", Kind.read);
			put("return", Kind.return_);
			put("void", Kind.void_);
			put("while", Kind.while_);

		}
	};

	public ScannerImpl(Reader r) {
		super(r);
		in = r;
		line = 1;
		col = 0;
		nextCh();
	}

	@Override
	public Token next() {
		while (Character.isWhitespace(ch)) {
			nextCh(); // skip white space
		}
		Token t = new Token(none, line, col);
		switch (ch) {
		// ----- identifier or keyword
		case 'a':
		case 'b':
		case 'c':
		case 'd':
		case 'e':
		case 'f':
		case 'g':
		case 'h':
		case 'i':
		case 'j':
		case 'k':
		case 'l':
		case 'm':
		case 'n':
		case 'o':
		case 'p':
		case 'q':
		case 'r':
		case 's':
		case 't':
		case 'u':
		case 'v':
		case 'w':
		case 'x':
		case 'y':
		case 'z':

		case 'A':
		case 'B':
		case 'C':
		case 'D':
		case 'E':
		case 'F':
		case 'G':
		case 'H':
		case 'I':
		case 'J':
		case 'K':
		case 'L':
		case 'M':
		case 'N':
		case 'O':
		case 'P':
		case 'Q':
		case 'R':
		case 'S':
		case 'T':
		case 'U':
		case 'V':
		case 'W':
		case 'X':
		case 'Y':
		case 'Z':
			readName(t); // distinguish between identifier and keyword
			break;
		// ----- number
		case '0':
		case '1':
		case '2':
		case '3':
		case '4':
		case '5':
		case '6':
		case '7':
		case '8':
		case '9':
			readNumber(t);
			break;

		// ----- simple tokens
		case ';':
			t.kind = semicolon;
			nextCh();
			break;
		case EOF:
			t.kind = eof;
			break;
		// ----- compound tokens
		case '=':
			nextCh();
			if (ch == '=') {
				t.kind = eql;
				nextCh();
			} else {
				t.kind = assign;
			}
			break;
		case '/':
			nextCh();
			if (ch == '*') {
				skipComment(t);
				t = next();
			} else if (ch == '=') {
				t.kind = slashas;
				nextCh();
			} else {
				t.kind = slash;
			}
			break;
		case '%':
			nextCh();
			if (ch == '=') {
				t.kind = remas;
				nextCh();
			} else {
				t.kind = rem;
			}
			break;
		case '!':
			nextCh();
			if (ch == '=') {
				t.kind = neq;
				nextCh();
			} else {
				error(t, Message.INVALID_CHAR, '!');
			}
			break;
		case '<':
			nextCh();
			if (ch == '=') {
				t.kind = leq;
				nextCh();
			} else {
				t.kind = lss;
			}
			break;
		case '>':
			nextCh();
			if (ch == '=') {
				t.kind = geq;
				nextCh();
			} else {
				t.kind = gtr;
			}
			break;
		case '&':
			nextCh();
			if (ch == '&') {
				t.kind = and;
				nextCh();
			} else {
				error(t, Message.INVALID_CHAR, '&');
			}
			break;
		case '|':
			nextCh();
			if (ch == '|') {
				t.kind = or;
				nextCh();
			} else {
				error(t, Message.INVALID_CHAR, '|');
			}
			break;
		case '+':
			nextCh();
			if (ch == '=') {
				t.kind = plusas;
				nextCh();
			} else if (ch == '+') {
				t.kind = pplus;
				nextCh();
			} else {
				t.kind = plus;
			}
			break;
		case '-':
			nextCh();
			if (ch == '=') {
				t.kind = minusas;
				nextCh();
			} else if (ch == '-') {
				t.kind = mminus;
				nextCh();
			} else {
				t.kind = minus;
			}
			break;
		case '*':
			nextCh();
			if (ch == '=') {
				t.kind = timesas;
				nextCh();
			} else {
				t.kind = times;
			}
			break;
		case ',':
			t.kind = comma;
			nextCh();
			break;
		case '.':
			t.kind = period;
			nextCh();
			break;
		case '(':
			t.kind = lpar;
			nextCh();
			break;
		case ')':
			t.kind = rpar;
			nextCh();
			break;
		case '[':
			t.kind = lbrack;
			nextCh();
			break;
		case ']':
			t.kind = rbrack;
			nextCh();
			break;
		case '{':
			t.kind = lbrace;
			nextCh();
			break;
		case '}':
			t.kind = rbrace;
			nextCh();
			break;
		case '\'':
			readCharConst(t);
			break;
		default:
			error(t, Message.INVALID_CHAR, ch);
			nextCh();
			break;
		}
		return t;
	}

	private void nextCh() {
		try {

			ch = (char) in.read();

			if (ch != EOF) {
				col++;
			}
			if (ch == '\n') {
				col = 0;
				line++;
			}
			if (ch == '\r') {
				col++;
				nextCh();
			}
		} catch (IOException e) {
			ch = EOF;
		}
	}

	private void readName(Token t) {
		StringBuilder builder = new StringBuilder();
		builder.append(ch);
		nextCh();
		while (ch != EOF && isIdent()) {
			builder.append(ch);
			nextCh();
		}
		if (commands.containsKey(builder.toString())) {
			t.str = builder.toString();
			t.kind = commands.get(builder.toString());
		} else {
			t.str = builder.toString();
			t.kind = Kind.ident;
		}

	}

	private boolean isIdent() {
		return (Character.isDigit(ch) || Character.isAlphabetic(ch)
				|| ch == '_');
	}

	private void readNumber(Token t) {
		StringBuilder number = new StringBuilder();
		t.kind = Kind.number;
		number.append(ch);
		nextCh();
		while (ch != EOF && Character.isDigit(ch)) {
			number.append(ch);
			nextCh();
		}

		try {
			t.val = Integer.parseInt(number.toString());
		} catch (NumberFormatException e) {
			t.val = 0;
			error(t, Message.BIG_NUM, number.toString());
		}

	}

	private void readCharConst(Token t) {
		t.kind = Kind.charConst;
		nextCh();
		if (ch == '\'') {
			error(t, Message.EMPTY_CHARCONST);
			nextCh();
			return;
		} else if (ch == '\\') {
			nextCh();
			if (ch != EOF) {

				switch (ch) {
				case '\'':
					t.val = '\'';
					break;
				case '\\':
					t.val = '\\';
					break;
				case 'r':
					t.val = '\r';
					break;
				case 'n':
					t.val = '\n';
					break;
				default:
					error(t, Message.UNDEFINED_ESCAPE, ch);
					break;
				}
			}
		} else if (ch == '\n') {
			error(t, Message.ILLEGAL_LINE_END);
			return;
		} else if (ch == '\r') {
			t.val = '\r';
		} else {
			t.val = ch;
		}
		checkCharEnd(t);

	}

	private void checkCharEnd(Token t) {
		nextCh();
		if (ch != '\'') {
			error(t, Message.MISSING_QUOTE);
		} else {
			nextCh();
		}
	}

	private void skipComment(Token t) {
		int comments = 1;
		nextCh();
		char pastCh;
		while (comments > 0 && ch != EOF) {
			pastCh = ch;
			nextCh();
			if (pastCh == '/' && ch == '*') {
				comments++;
				nextCh();
			} else if (pastCh == '*' && ch == '/') {
				comments--;
				if (comments > 0) {
					nextCh();
				}
			}
		}
		if (ch == EOF && comments > 0) {
			error(t, Message.EOF_IN_COMMENT);
		}
		nextCh();
	}
}
