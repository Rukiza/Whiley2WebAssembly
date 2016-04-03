package wywasm;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.ArrayList;

import wyil.io.WyilFilePrinter;
import wyil.io.WyilFileReader;
import wyil.lang.*;

public class WasmFileWriter {
	private PrintStream output;
	private StringBuilder wasmBuilder;
	
	public WasmFileWriter(PrintStream output) {
		this.output = output;
		wasmBuilder = new StringBuilder();
	}
	
	public void write(WyilFile file) throws IOException {
		wasmBuilder.append("(")
				.append("module")
				.append("\n");


		for(WyilFile.FunctionOrMethod d : file.functionOrMethods()) {
			indent(4);
			wasmBuilder.append("(")
					.append("export")
					.append(" ")
					.append("\"")
					.append(d.name())
					.append("\"")
					.append(" ")
					.append("$")
					.append(d.name())
					.append(")")
					.append("\n");
			write(d, 4);
		}

		wasmBuilder.append(")");
		output.println(wasmBuilder.toString());
		PrintStream out = new PrintStream(new FileOutputStream("wasm/test.wast"));
		out.print(wasmBuilder.toString());
		out.close();
	}
	
	/**
	 * Translate a function or method into WebAssembly
	 * 
	 * @param d
	 * @throws IOException 
	 */
	private void write(WyilFile.FunctionOrMethod d, int indent) throws IOException {
		indent(indent);
		wasmBuilder.append("(")
				.append("func")
				.append(" ")
				.append("$")
				.append(d.name());

		if (!d.type().params().isEmpty()){
			writeParams(d.type().params());
		}

		if (!d.type().returns().isEmpty()){
			writeReturns(d.type().returns());
		}

		wasmBuilder.append("\n");

		output.println(d.body().indices());
		writeLocalVars(d.body().indices(), d.type().params().size(), d.type().returns().size(), indent + 4);

		output.print(d.name());
		output.print(" ");
		output.println(d.type());
		if(d.body() != null) {			
			write(d.body(),indent + 4);
		}
		indent(indent);
		output.println();
		wasmBuilder.append(")")
				.append("\n");
	}

	private void writeLocalVars(List<CodeBlock.Index> indices, int numOfParm, int numOfRet, int indent){
		int i = numOfParm + numOfRet;
		for ( ;i < indices.size(); i++) {
			indent(indent);
			wasmBuilder.append("(")
					.append("local")
					.append(" ")
					.append("$")
					.append(indices.get(i))
					.append(" ")
					.append("i32") //FIXME: Ints of Floats
					.append(")")
					.append("\n");
		}
	}

	private void writeParams(List<Type> params){
		int i = 0;
		for (Type parm: params) {
			wasmBuilder.append(" ")
					.append("(")
					.append("param")
					.append(" ")
					.append("$")
					.append(i)
					.append(" ")
					.append("i32") // FIXME: ints of floats
					.append(")");
		}
	}

	private void writeReturns(List<Type> returns){
		for (Type ret: returns) {
			wasmBuilder.append(" ")
					.append("(")
					.append("result")
					.append(" ")
					.append("i32") //TODO: sort out if return type is not int
					.append(")");
		}
	}

	private void write(CodeBlock c, int indent) {
		for(Code bytecode : c.bytecodes()) {
			indent(indent);
			if (bytecode.opcode() == Code.OPCODE_const) {
				write((Codes.Const) bytecode, indent);
			} else if (bytecode.opcode() == Code.OPCODE_add) {
				write((Codes.BinaryOperator) bytecode, indent);
			} else if (bytecode.opcode() == Code.OPCODE_return) {
				write((Codes.Return) bytecode, indent);
			} else if (bytecode.opcode() == Code.OPCODE_sub) {
				write((Codes.BinaryOperator) bytecode, indent);
			} else if (bytecode.opcode() == Code.OPCODE_div) {
				write((Codes.BinaryOperator) bytecode, indent);
			} else if (bytecode.opcode() == Code.OPCODE_mul) {
				write((Codes.BinaryOperator) bytecode, indent);
			} else if (bytecode.opcode() == Code.OPCODE_invokefn) {
				write((Codes.Invoke) bytecode, indent);
			}

			/*
			if(bytecode instanceof Code.Compound) {
				output.println("\t" + bytecode.getClass().getName() + " {");
				write((Code.Compound) bytecode,indent+4);
				output.println("}");
			}
			 else {
				output.println("\t" + bytecode);
			}
			*/
		}
	}

	private void write(Codes.Const c, int indent) {
		wasmBuilder.append("(")
				.append("set_local")
				.append(" ")
				.append("$")
				.append(c.target())
				.append(" ")
				.append("(i32.const")
				.append(" ")
				.append(c.constant)
				.append(")")
				.append(")")
				.append("\n");
		output.println("\t" + c);
	}

	private void write(Codes.BinaryOperator c, int indent) {
		wasmBuilder.append("(");
		if (c.opcode() == Code.OPCODE_add) { //TODO: Create method that make a i32.add or get_local / Set local
			wasmBuilder.append("set_local")
					.append(" ")
					.append("$")
					.append(c.target(0))
					.append(" ")
					.append("(")
					.append("i32.add")
					.append(" ")
					.append("(")
					.append("get_local")
					.append(" ")
					.append("$")
					.append(c.operand(0))
					.append(")")
					.append(" ")
					.append("(")
					.append("get_local")
					.append(" ")
					.append("$")
					.append(c.operand(1))
					.append(")")
					.append(")");
		} else if (c.opcode() == Code.OPCODE_sub) {
			wasmBuilder.append("set_local")
					.append(" ")
					.append("$")
					.append(c.target(0))
					.append(" ")
					.append("(")
					.append("i32.sub")
					.append(" ")
					.append("(")
					.append("get_local")
					.append(" ")
					.append("$")
					.append(c.operand(0))
					.append(")")
					.append(" ")
					.append("(")
					.append("get_local")
					.append(" ")
					.append("$")
					.append(c.operand(1))
					.append(")")
					.append(")");
		} else if (c.opcode() == Code.OPCODE_div) {
			wasmBuilder.append("set_local")
					.append(" ")
					.append("$")
					.append(c.target(0))
					.append(" ")
					.append("(")
					.append("i32.div_s") //TODO: Work out if sign or unsigned is appropriate here.
					.append(" ")
					.append("(")
					.append("get_local")
					.append(" ")
					.append("$")
					.append(c.operand(0))
					.append(")")
					.append(" ")
					.append("(")
					.append("get_local")
					.append(" ")
					.append("$")
					.append(c.operand(1))
					.append(")")
					.append(")");
		} else if (c.opcode() == Code.OPCODE_mul) {
			wasmBuilder.append("set_local")
					.append(" ")
					.append("$")
					.append(c.target(0))
					.append(" ")
					.append("(")
					.append("i32.mul")
					.append(" ")
					.append("(")
					.append("get_local")
					.append(" ")
					.append("$")
					.append(c.operand(0))
					.append(")")
					.append(" ")
					.append("(")
					.append("get_local")
					.append(" ")
					.append("$")
					.append(c.operand(1))
					.append(")")
					.append(")");

		}
		wasmBuilder.append(")")
				.append("\n");
		output.println("\t" + c);
	}

	private void write(Codes.Return c, int indent) {
		if (c.operands().length == 0){
		} else {
			wasmBuilder.append("(")
					.append("get_local")
					.append(" ")
					.append("$")
					.append(c.operand(0))
					.append(")");
		}
		wasmBuilder.append("\n");
		output.println("\t" + c);
	}

	private void write(Codes.Invoke c, int indent) {//TODO:Make it so that functions can call functions from other files.
		wasmBuilder.append("(")
				.append("set_local")
				.append(" ")
				.append("$")
				.append(c.target(0))
				.append(" ")
				.append("(")
				.append("call")
				.append(" ")
				.append("$")
				.append(c.name.name());
		for (int operand: c.operands()) {
			wasmBuilder.append(" ")
					.append("(")
					.append("get_local")
					.append(" ")
					.append("$")
					.append(operand)
					.append(")");
		}
		wasmBuilder.append(")")
				.append(")")
				.append("\n");
		output.println("\t" + c);
	}

	private void indent(int indent) {
		for(int i=0;i!=indent;++i) {
			output.print(" ");
			wasmBuilder.append(" ");
		}
	}
	
	public static void main(String[] args) {
		try {
			// First, read the input file to generate WyilFile instance
			WyilFile file = new WyilFileReader(args[0]).read();
			// Second, pass WyilFile into wasm file writer
			new WasmFileWriter(System.out).write(file);
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
}
