package wywasm;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import wyil.io.WyilFilePrinter;
import wyil.io.WyilFileReader;
import wyil.lang.*;
import wyil.util.AttributedCodeBlock;

public class WasmFileWriter {

	//Bool values.
	private static final int TRUE = 1;
	private static final int FALSE = 0;

	//Functions
	private static final Map<String, List<Type>> paramMap = new HashMap<>();
	private static final Map<String, List<Type>> returnMap = new HashMap<>();

	//TYPES
	private static final String INT = "i32";
	private static final String BOOL = "i32";

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
			paramMap.put(d.name(), d.type().params());
			returnMap.put(d.name(), d.type().returns());

		}


		wasmBuilder.append(")");
		output.println(wasmBuilder.toString());
		//Needs to create file
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

		//Map<CodeBlock.Index, Boolean> variableMap = loadVerables(d.body().indices());
		List<Integer> variableList = new ArrayList<>();

		if (!d.type().params().isEmpty()){
			writeParams(d.type().params(), variableList);
		}

		if (!d.type().returns().isEmpty()){
			writeReturns(d.type().returns(), variableList);
		}

		wasmBuilder.append("\n");

		//output.println(d.body().indices());

		//writeLocalVars(d, indent + 4);

		//local variables need to be set up at the start of each function
		//Variable need to have a type at this stage
		//Invoke/BinaryOperators/Const - need them.
		if (d.body() != null) {
			wasmBuilder.append(writeVariable(d.body(),variableList, indent +4));
		}

		output.print(d.name());
		output.print(" ");
		output.println(d.type());
		if(d.body() != null) {
			write(d.body(), indent + 4);
		}
		indent(indent);
		output.println();
		wasmBuilder.append(")")
				.append("\n");
	}





	private void writeParams(List<Type> params, List<Integer> variableList){
		int i = 0;
		for (Type param: params) {
			wasmBuilder.append(" ")
					.append("(")
					.append("param")
					.append(" ")
					.append("$")
					.append(i)
					.append(" ")
					.append(getType(param)) // FIXME: ints, floats, bool UPDATE: Fixed needs testing
					.append(")");
			variableList.add(i);
		}

	}

	private void writeReturns(List<Type> returns, List<Integer> variableList){
		for (Type ret: returns) {
			wasmBuilder.append(" ")
					.append("(")
					.append("result")
					.append(" ")
					.append(getType(ret)) //TODO: sort out if return type is not int UPDATE: Fixed needs testing
					.append(")");
		}
	}

	private void write(CodeBlock c, int indent) {
		for(Code bytecode : c.bytecodes()) {
			indent(indent);
			if (bytecode instanceof Codes.ArrayGenerator){
				output.print("ArrayGenerator: ");
			} else if (bytecode instanceof Codes.Assert) {
				output.print("Assert: ");
			} else if (bytecode instanceof Codes.Assign) {
				output.print("Assign: ");
			} else if (bytecode instanceof Codes.Assume) {

			} else if (bytecode instanceof Codes.BinaryOperator) {
				write((Codes.BinaryOperator) bytecode, indent);
			} else if (bytecode instanceof Codes.Const) {
				write((Codes.Const) bytecode, indent);
			} else if (bytecode instanceof Codes.Convert) {

			} else if (bytecode instanceof Codes.Debug) {

			} else if (bytecode instanceof Codes.Dereference) {

			} else if (bytecode instanceof Codes.Fail) {

			} else if (bytecode instanceof Codes.FieldLoad) {

			} else if (bytecode instanceof Codes.Goto) {

			} else if (bytecode instanceof Codes.If) {
				output.print("If: ");
			} else if (bytecode instanceof Codes.IfIs) {
				output.print("IfIs: ");
			} else if (bytecode instanceof Codes.IndexOf) {

			} else if (bytecode instanceof Codes.IndirectInvoke) {

			} else if (bytecode instanceof Codes.Invariant) {

			} else if (bytecode instanceof Codes.Invert) {

			} else if (bytecode instanceof Codes.Invoke) {
				write((Codes.Invoke) bytecode, indent);
			}else if (bytecode instanceof Codes.Label) {
				output.print("Label: ");
			} else if (bytecode instanceof Codes.Lambda) {

			} else if (bytecode instanceof Codes.LengthOf) {

			} else if (bytecode instanceof Codes.Loop) {

			} else if (bytecode instanceof Codes.Move) {

			} else if (bytecode instanceof Codes.NewArray) {

			} else if (bytecode instanceof Codes.NewObject) {

			} else if (bytecode instanceof Codes.NewRecord) {

			} else if (bytecode instanceof Codes.Nop) {

			} else if (bytecode instanceof Codes.Not) {

			} else if (bytecode instanceof Codes.Quantify) {

			} else if (bytecode instanceof Codes.Return) {
				write((Codes.Return) bytecode, indent);
			} else if (bytecode instanceof Codes.Switch) {

			} else if (bytecode instanceof Codes.UnaryOperator) {

			} else if (bytecode instanceof Codes.Update) {

			} else if (bytecode instanceof Codes.Void) {

			}

			if(bytecode instanceof Code.Compound) {
				output.println("\t" + bytecode.getClass().getName() + " {");
				write((Code.Compound) bytecode, indent+4);
				output.println("}");
			}
			 else {
				output.println("\t" + bytecode);
			}

		}
	}

	private void write(Codes.Const c, int indent) {
		wasmBuilder.append("(")
				.append("set_local")
				.append(" ");

		wasmBuilder.append("$")
				.append(c.target())
				.append(" ")
				.append("(");

		if (c.constant.type().equals(Type.T_BOOL)) {
			wasmBuilder.append("i32");
			wasmBuilder.append(".const")
					.append(" ");
			if ("true".equals(c.constant.toString())) {
				wasmBuilder.append(TRUE);
			} else {
				wasmBuilder.append(FALSE);
			}
			wasmBuilder.append(")");
		}
		if (c.constant.type().equals(Type.T_INT)) {
			wasmBuilder.append("i32"); //TODO: Make it work with more than just ints
			wasmBuilder.append(".const")
					.append(" ")
					.append(c.constant)
					.append(")");
		}
		wasmBuilder.append(")")
				.append("\n");
		//output.println("\t" + c);
	}

	/**
	 * Being used to make local variables.
	 * @param c
	 * @param indent
     */
	private void writeLocalVar(Codes.Const c, int indent) {
		wasmBuilder.append("(")
				.append("local")
				.append(" ")
				.append("$")
				.append(c.target())
				.append(" ");
		if (c.constant.type().equals(Type.T_INT)) {
			wasmBuilder.append("i32");
		}
		wasmBuilder.append(")")
				.append("\n");
		indent(indent);
	}

	private void writeLocalVar(Codes.BinaryOperator c, int indent) {
		wasmBuilder.append("(")
				.append("local")
				.append(" ")
				.append("$")
				.append(c.target(0))
				.append(" ");
		output.println("TYPE:"+c.type(0));
		//TODO:Work with all the types - assumption first is target type
		if (c.type(0).equals(Type.T_INT)){
			wasmBuilder.append("i32");
		}
		wasmBuilder.append(")")
				.append("\n");
		indent(indent);
	}


	private void write(Codes.BinaryOperator c, int indent) {
		//TODO: add the ability to have more targets
		//Dosent work currently.
		/*
		if (!variableList.contains(c.target(0))){
			writeLocalVar(c, indent);
			variableList.add(c.target(0));
			//wasmBuilder.append(variableList);
		}
		*/
		wasmBuilder.append("(")
				.append("set_local")
				.append(" ")
				.append("$")
				.append(c.target(0))//FIXME: May not be correct.
				.append(" ");
		if (c.opcode() == Code.OPCODE_add) { //TODO: Create method that make a i32.add or get_local / Set local
			wasmBuilder.append("(")
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
		wasmBuilder.append("(")
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
			wasmBuilder.append("(")
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
			wasmBuilder.append("(")
					.append("i32")
					.append(".mul")
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
		//output.println("\t" + c);
	}

//	private void writeOperand(String ){
//
//	}

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
		//output.println("\t" + c);
	}

	private void write(Codes.Invoke c, int indent) {//TODO:Make it so that functions can call functions from other files.
		output.println();

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
		//output.println("\t" + c);
	}

	private void indent(int indent) {
		for(int i=0;i!=indent;++i) {
			output.print(" ");
			wasmBuilder.append(" ");
		}
	}

	private void indent(StringBuilder builder, int indent) {
		for(int i=0;i!=indent;++i) {
			builder.append(" ");
		}
	}


	//TODO: Remove un need calls.
	private String writeVariable(CodeBlock d, List<Integer> variableList, int indent) {
		StringBuilder localVars = new StringBuilder();
		for (Code bytecode: d.bytecodes()) {
			if (bytecode instanceof Codes.ArrayGenerator){
				output.print("ArrayGenerator: ");
			} else if (bytecode instanceof Codes.Assert) {
				output.print("Assert: ");
			} else if (bytecode instanceof Codes.Assign) {
				output.print("Assign: ");
			} else if (bytecode instanceof Codes.Assume) {

			} else if (bytecode instanceof Codes.BinaryOperator) {
				writeVariable((Codes.BinaryOperator) bytecode, variableList, localVars, indent);
			} else if (bytecode instanceof Codes.Const) {
				writeVariable((Codes.Const) bytecode, variableList, localVars, indent);
			} else if (bytecode instanceof Codes.Convert) {

			} else if (bytecode instanceof Codes.Debug) {

			} else if (bytecode instanceof Codes.Dereference) {

			} else if (bytecode instanceof Codes.Fail) {

			} else if (bytecode instanceof Codes.FieldLoad) {

			} else if (bytecode instanceof Codes.Goto) {

			} else if (bytecode instanceof Codes.If) {
				output.print("If: ");
			} else if (bytecode instanceof Codes.IfIs) {
				output.print("IfIs: ");
			} else if (bytecode instanceof Codes.IndexOf) {

			} else if (bytecode instanceof Codes.IndirectInvoke) {

			} else if (bytecode instanceof Codes.Invariant) {

			} else if (bytecode instanceof Codes.Invert) {

			} else if (bytecode instanceof Codes.Invoke) {
				writeVariable((Codes.Invoke) bytecode, variableList, localVars, indent);
			}else if (bytecode instanceof Codes.Label) {
				output.print("Label: ");
			} else if (bytecode instanceof Codes.Lambda) {

			} else if (bytecode instanceof Codes.LengthOf) {

			} else if (bytecode instanceof Codes.Loop) {

			} else if (bytecode instanceof Codes.Move) {

			} else if (bytecode instanceof Codes.NewArray) {

			} else if (bytecode instanceof Codes.NewObject) {

			} else if (bytecode instanceof Codes.NewRecord) {

			} else if (bytecode instanceof Codes.Nop) {

			} else if (bytecode instanceof Codes.Not) {

			} else if (bytecode instanceof Codes.Quantify) {

			} else if (bytecode instanceof Codes.Switch) {

			} else if (bytecode instanceof Codes.UnaryOperator) {

			} else if (bytecode instanceof Codes.Update) {

			} else if (bytecode instanceof Codes.Void) {

			}
		}
		return localVars.toString();
	}

	/**
	 * Writes a local variable of an invocation call and adds it to the list of made vars if not there.
	 * @param bytecode
	 * @param variableList
	 * @param localVars
     * @param indent
     */
	private void writeVariable(Codes.Invoke bytecode, List<Integer> variableList, StringBuilder localVars, int indent) {
		if (variableList.contains(bytecode.target(0))){
			return;
		}
		else {
			variableList.add(bytecode.target(0));
		}
		indent(localVars,indent);
		List<Type> targets = returnMap.get(bytecode.name.name());

		Type targetType = targets.get(0);

		localVars.append(writeVariable(bytecode.target(0),getType(targetType)));
	}

	/**
	 * Writes a local variable that is used in a constant call.
	 * @param bytecode
	 * @param variableList
	 * @param localVars
     * @param indent
     */
	private void writeVariable(Codes.Const bytecode, List<Integer> variableList, StringBuilder localVars, int indent) {
		if (variableList.contains(bytecode.target(0))){
			return;
		}
		else {
			variableList.add(bytecode.target(0));
		}
		indent(localVars,indent);
		localVars.append(writeVariable(bytecode.target(),getType(bytecode.constant.type())));
	}

	/**
	 * Looks at a list of variable if its not there will make the list variable and add it to the list.
	 * @param bytecode
	 * @param variableList
	 * @param localVars
     * @param indent
     */
	private void writeVariable(Codes.BinaryOperator bytecode, List<Integer> variableList, StringBuilder localVars, int indent){
		if (variableList.contains(bytecode.target(0))){
			return;
		}
		else {
			variableList.add(bytecode.target(0));
		}
		indent(localVars,indent);
		//TODO:Work with all the types - assumption first is target type possible get type method.
		localVars.append(writeVariable(bytecode.target(0),getType(bytecode.type(0))));
	}

	private String writeVariable(int target, String type) {
		StringBuilder builder = new StringBuilder();
		builder.append("(")
				.append("local")
				.append(" ")
				.append("$")
				.append(target)
				.append(" ")
				.append(type)
				.append(")")
				.append("\n");

		return builder.toString();
	}

	private String getType(Type t){
		if (t.equals(Type.T_INT)) {
			return INT;
		} else if (t.equals(Type.T_BOOL)) {
			return BOOL;
		}
		return "";
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
