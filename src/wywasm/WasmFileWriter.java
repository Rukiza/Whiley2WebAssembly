package wywasm;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import ast.*;
import util.WastFactory;
import wyil.io.WyilFileReader;
import wyil.lang.*;

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

	//OPCODES
	private static final String ADD = "add";
	private static final String SUB = "sub";
	private static final String DIV = "div_s";//TODO: Change when code changes.
	private static final String MUL = "mul";
	private static final String BITWISE_OR = "or";
	private static final String BITWISE_XOR = "xor";
	private static final String BITWISE_AND = "and";
	private static final String REM = "rem_s";//TODO: Change when code changes.

	//OPCODE EXTENSIONS
	private static final String SIGNED = "_s";
	private static final String UNSIGNED = "_u";
	private static final String NO_EXTENTION = "";

	private PrintStream output;
	private StringBuilder wasmBuilder;
	private WastFactory factory;
	
	public WasmFileWriter(PrintStream output, WastFactory factory) {
		this.output = output;
		wasmBuilder = new StringBuilder();
		this.factory = factory;
	}
	
	public void write(WyilFile file) throws IOException {
		wasmBuilder.append("(")
				.append("module")
				.append("\n");


		List<ModuleElement.Export> exports = new ArrayList<>();
		List<Function> functions = new ArrayList<>();
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
			functions.add(write(d, 4));
			exports.add(factory.createExport("\""+d.name()+"\"",factory.createVar("$"+d.name())));
			paramMap.put(d.name(), d.type().params());
			returnMap.put(d.name(), d.type().returns());

		}

		Module module = factory.createModule(null,functions,null,exports,null,null,null);



		wasmBuilder.append(")");
		output.println(wasmBuilder.toString());
		//Needs to create file
		PrintStream out = new PrintStream(new FileOutputStream("wasm/test.wast"));
		BufferedOutputStream out2 = new BufferedOutputStream(new FileOutputStream("wasm/test2.wast"));
		module.write(out2, 0);
		out2.close();
		out.print(wasmBuilder.toString());
		out.close();

	}
	
	/**
	 * Translate a function or method into WebAssembly
	 * 
	 * @param d
	 * @throws IOException 
	 */
	private Function write(WyilFile.FunctionOrMethod d, int indent) throws IOException {
		indent(indent);
		wasmBuilder.append("(")
				.append("func")
				.append(" ")
				.append("$")
				.append(d.name());

		//Map<CodeBlock.Index, Boolean> variableMap = loadVerables(d.body().indices());
		List<Integer> variableList = new ArrayList<>();

		List<FunctionElement.Param> params = null;
		if (!d.type().params().isEmpty()){
			params = writeParams(d.type().params(), variableList);
		}

		FunctionElement.Result result = null;
		if (!d.type().returns().isEmpty()){
			result = writeReturns(d.type().returns(), variableList);
		}

		wasmBuilder.append("\n");

		//output.println(d.body().indices());

		//writeLocalVars(d, indent + 4);

		//local variables need to be set up at the start of each function
		//Variable need to have a type at this stage
		//Invoke/BinaryOperators/Const - need them.

		List<FunctionElement.Local> locals = null;
		if (d.body() != null) {
			locals = writeVariable(d.body(),variableList, indent +4);
		}

		output.print(d.name());
		output.print(" ");
		output.println(d.type());
		List<Expr> exprs = null;
		if(d.body() != null) {
			exprs = write(d.body(), indent + 4);
		}
		indent(indent);
		output.println();
		wasmBuilder.append(")")
				.append("\n");
		return factory.createFunction("$"+d.name(), null, params, result, locals, exprs);
	}

	private List<FunctionElement.Param> writeParams(List<Type> params, List<Integer> variableList){
		int i = 0;
		List<FunctionElement.Param> pars = new ArrayList<>();
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
			pars.add(factory.createParam("$"+i, factory.createExprType(getType(param))));
		}
		return pars;
	}

	private FunctionElement.Result writeReturns(List<Type> returns, List<Integer> variableList){
		for (Type ret: returns) {
			wasmBuilder.append(" ")
					.append("(")
					.append("result")
					.append(" ")
					.append(getType(ret)) //TODO: sort out if return type is not int UPDATE: Fixed needs testing
					.append(")");
		}
		if (!returns.isEmpty()) {
			return factory.createResult(factory.createExprType(getType(returns.get(0))));
		} else {
			return null; //Todo inform that this happens
		}
	}

	private List<Expr> write(CodeBlock c, int indent) {
		List<Expr> exprs = new ArrayList<>();
		for(Code bytecode : c.bytecodes()) {
			indent(indent);
			Expr expr = write(bytecode, indent);
			if (expr == null) {
			}else {
				exprs.add(expr);
			}

			if(bytecode instanceof Code.Compound) {
				output.println("\t" + bytecode.getClass().getName() + " {");
				write((CodeBlock) bytecode, indent+4);
				output.println("}");
			}
			 else {
				output.println("\t" + bytecode);
			}

		}
		return exprs;
	}

	private Expr write(Code bytecode, int indent) {
		if (bytecode instanceof Codes.ArrayGenerator){
		} else if (bytecode instanceof Codes.Assert) {
		} else if (bytecode instanceof Codes.Assign) {
		} else if (bytecode instanceof Codes.Assume) {
		} else if (bytecode instanceof Codes.BinaryOperator) {
			return write((Codes.BinaryOperator) bytecode, indent);
		} else if (bytecode instanceof Codes.Const) {
			return write((Codes.Const) bytecode, indent);
		} else if (bytecode instanceof Codes.Convert) {
		} else if (bytecode instanceof Codes.Debug) {
		} else if (bytecode instanceof Codes.Dereference) {
		} else if (bytecode instanceof Codes.Fail) {
		} else if (bytecode instanceof Codes.FieldLoad) {
		} else if (bytecode instanceof Codes.Goto) {
		} else if (bytecode instanceof Codes.If) {
		} else if (bytecode instanceof Codes.IfIs) {
		} else if (bytecode instanceof Codes.IndexOf) {
		} else if (bytecode instanceof Codes.IndirectInvoke) {
		} else if (bytecode instanceof Codes.Invariant) {
		} else if (bytecode instanceof Codes.Invert) {
		} else if (bytecode instanceof Codes.Invoke) {
			return write((Codes.Invoke) bytecode, indent);
		} else if (bytecode instanceof Codes.Label) {
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
			return write((Codes.Return) bytecode, indent);
		} else if (bytecode instanceof Codes.Switch) {
		} else if (bytecode instanceof Codes.UnaryOperator) {
		} else if (bytecode instanceof Codes.Update) {
		} else if (bytecode instanceof Codes.Void) {
		}
		throw new Error("Some error"); //TODO: Change that.
	}

	private Expr write(Codes.Const c, int indent) {
		wasmBuilder.append("(")
				.append("set_local")
				.append(" ");

		wasmBuilder.append("$")
				.append(c.target())
				.append(" ")
				.append("(");

		if (c.constant.type().equals(Type.T_BOOL)) {
			wasmBuilder.append(getType(c.constant.type()));
			wasmBuilder.append(".const")
					.append(" ");
			if ("true".equals(c.constant.toString())) {
				wasmBuilder.append(TRUE);
			} else {
				wasmBuilder.append(FALSE);
			}
			wasmBuilder.append(")");
		}else {
			wasmBuilder.append(getType(c.constant.type())); //TODO: Make it work with more than just ints
			wasmBuilder.append(".const")
					.append(" ")
					.append(c.constant)
					.append(")");
		}
		wasmBuilder.append(")")
				.append("\n");
		return factory.createSetLocal(factory.createVar("$"+c.target()),
				factory.createConst(writeConstantType(c.constant.type()), writeConstantValue(c.constant)));
	}

	private ExprElement.Type writeConstantType(Type type) {
		if (type.equals(Type.T_INT)) {
			return factory.createExprType(Expr.INT);
		} else if (type.equals(Type.T_BOOL)) {
			return factory.createExprType(Expr.INT);
		}
		//Todo throw error
		throw new Error("Some error to be decided later.");
	}

	private ExprElement.Value writeConstantValue(Constant constant) {
		if (constant.type().equals(Type.T_INT)) {
			return factory.createValue(new Integer(constant.toString()));
		} else if (constant.type().equals(Type.T_BOOL)) {
			if ("true".equals(constant.toString())) {
				return factory.createValue(TRUE);
			} else {
				return factory.createValue(FALSE);
			}
		}
		//Todo throw error
		throw new Error("Some error to be decided later.");
	}

	private Expr write(Codes.BinaryOperator c, int indent) {
		//TODO: add the ability to have more targets
		wasmBuilder.append("(")
				.append("set_local")
				.append(" ")
				.append("$")
				.append(c.target(0))//FIXME: May not be correct.
				.append(" ")
				.append("(")
				.append(getType(c.type(0)))
				.append(".")
				.append(getOp(c.opcode()))//FIXME: Add in the difference from int and float operand calls.
				.append(" ")
				.append(getGetLocal(c.operand(0)))
				.append(" ")
				.append(getGetLocal(c.operand(1)))
				.append(")")
				.append(")")
				.append("\n");
		//output.println("\t" + c);
		return factory.createSetLocal(
				factory.createVar("$"+c.target(0)),
				factory.createBinOp(factory.createExprType(getType(c.type(0))),
						getOp(c.opcode()),
						factory.createGetLocal(
								factory.createVar("$"+c.operand(0))),
						factory.createGetLocal(
								factory.createVar("$"+c.operand(1)))));
	}

	private Expr write(Codes.Return c, int indent) {
		if (c.operands().length == 0){
			return null;
		} else {
			wasmBuilder.append(getGetLocal(c.operand(0)));
			wasmBuilder.append("\n");
			return factory.createGetLocal(
					factory.createVar("$"+c.operand(0)));
		}
	}

	private Expr write(Codes.Invoke c, int indent) {//TODO:Make it so that functions can call functions from other files.
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
		List<Expr> exprs = new ArrayList<>();
		for (int operand: c.operands()) {
			wasmBuilder.append(" ")
					.append(getGetLocal(operand));
			exprs.add(factory.createGetLocal(
				factory.createVar("$"+operand)
			));
		}
		wasmBuilder.append(")")
				.append(")")
				.append("\n");
		//output.println("\t" + c);
		return factory.createSetLocal(
				factory.createVar("$"+c.target(0)),
				factory.createCall(
						factory.createVar("$"+ c.name.name()),
						exprs
				)
		);
	}

	/**
	 * Indents the code.
	 * @param indent
     */
	private void indent(int indent) {
		for(int i=0;i!=indent;++i) {
			output.print(" ");
			wasmBuilder.append(" ");
		}
	}

	/**
	 * Indents a stringbuilder passed in.
	 * @param builder
	 * @param indent
     */
	private void indent(StringBuilder builder, int indent) {
		for(int i=0;i!=indent;++i) {
			builder.append(" ");
		}
	}


	//TODO: Remove un need calls.
	private List<FunctionElement.Local> writeVariable(CodeBlock d, List<Integer> variableList, int indent) {
		List<FunctionElement.Local> locals = new ArrayList<>();
		for (Code bytecode: d.bytecodes()) {
			if (bytecode instanceof Codes.ArrayGenerator){
			} else if (bytecode instanceof Codes.Assert) {
			} else if (bytecode instanceof Codes.Assign) {
			} else if (bytecode instanceof Codes.Assume) {
			} else if (bytecode instanceof Codes.BinaryOperator) {
				locals.add(writeVariable((Codes.BinaryOperator) bytecode, variableList, indent));
			} else if (bytecode instanceof Codes.Const) {
				locals.add(writeVariable((Codes.Const) bytecode, variableList, indent));
			} else if (bytecode instanceof Codes.Convert) {
			} else if (bytecode instanceof Codes.Debug) {
			} else if (bytecode instanceof Codes.Dereference) {
			} else if (bytecode instanceof Codes.Fail) {
			} else if (bytecode instanceof Codes.FieldLoad) {
			} else if (bytecode instanceof Codes.Goto) {
			} else if (bytecode instanceof Codes.If) {
			} else if (bytecode instanceof Codes.IfIs) {
			} else if (bytecode instanceof Codes.IndexOf) {
			} else if (bytecode instanceof Codes.IndirectInvoke) {
			} else if (bytecode instanceof Codes.Invariant) {
			} else if (bytecode instanceof Codes.Invert) {
			} else if (bytecode instanceof Codes.Invoke) {
				locals.add(writeVariable((Codes.Invoke) bytecode, variableList, indent));
			} else if (bytecode instanceof Codes.Label) {
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
		return locals;
	}

	/**
	 * Writes a local variable of an invocation call and adds it to the list of made vars if not there.
	 * @param bytecode
	 * @param variableList
	 * @param localVars
     * @param indent
     */
	private FunctionElement.Local writeVariable(Codes.Invoke bytecode, List<Integer> variableList, int indent) {
		if (variableList.contains(bytecode.target(0))){
			return null;
		}
		else {
			variableList.add(bytecode.target(0));
		}
		List<Type> targets = returnMap.get(bytecode.name.name());

		Type targetType = targets.get(0);

		return factory.createLocal("$"+bytecode.target(0),
				factory.createExprType(getType(targetType)));
	}

	/**
	 * Writes a local variable that is used in a constant call.
	 * @param bytecode
	 * @param variableList
	 * @param localVars
     * @param indent
     */
	private FunctionElement.Local writeVariable(Codes.Const bytecode, List<Integer> variableList, int indent) {
		if (variableList.contains(bytecode.target(0))){
			return null;
		}
		else {
			variableList.add(bytecode.target(0));
		}
		return factory.createLocal("$"+bytecode.target(),
				factory.createExprType(getType(bytecode.constant.type())));
	}

	/**
	 * Looks at a list of variable if its not there will make the list variable and add it to the list.
	 * @param bytecode
	 * @param variableList
	 * @param localVars
     * @param indent
     */
	private FunctionElement.Local writeVariable(Codes.BinaryOperator bytecode, List<Integer> variableList, int indent){
		if (variableList.contains(bytecode.target(0))){
			return null;
		}
		else {
			variableList.add(bytecode.target(0));
		}
		//TODO:Work with all the types - assumption first is target type possible get type method.
		return factory.createLocal("$"+bytecode.target(0),
				factory.createExprType(getType(bytecode.type(0))));
	}

	/**
	 * TODO: Do a returns a valuing in the form "Bal"
	 * @param target
	 * @param type
     * @return
     */
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

	/**
	 * Constructes a local variable.
	 * @param operand - operand used in constuction.
	 * @return
     */
	private String getGetLocal (int operand) {
		StringBuilder buider = new StringBuilder();
		buider.append("(")
				.append("get_local")
				.append(" ")
				.append("$")
				.append(operand)
				.append(")");
		return buider.toString();
	}

	/**
	 * TODO: Maby add to a map.
	 * @param t
	 * @return
     */
	private String getType(Type t){
		if (t.equals(Type.T_INT)) {
			return INT;
		} else if (t.equals(Type.T_BOOL)) {
			return BOOL;
		}
		return ""; //TODO: throw a error
	}

	/**
	 * For getting op codes
	 * TODO: Maby add to a map.
	 * @param opcode
	 * @return
     */
	private String getOp(int opcode) {
		switch (opcode) {
			case Code.OPCODE_mul:
				return MUL;
			case Code.OPCODE_add:
				return ADD;
			case Code.OPCODE_div:
				return DIV;
			case Code.OPCODE_sub:
				return SUB;
			default:
				return "";
		}
	}

	public static void main(String[] args) {
		try {
			// First, read the input file to generate WyilFile instance
			WyilFile file = new WyilFileReader(args[0]).read();
			// Second, pass WyilFile into wasm file writer
			new WasmFileWriter(System.out, new WastFactory.SWastFactory()).write(file);
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
}
