package wywasm;

import java.io.*;
import java.util.*;

import ast.*;
import util.WastFactory;
import wyil.io.WyilFileReader;
import wyil.lang.*;

import javax.annotation.processing.SupportedSourceVersion;

public class WasmFileWriter {

	//Bool values.
	private static final int TRUE = 1;
	private static final int FALSE = 0;

	//Functions TODO: remove if un-needed
	private Map<String, List<Type>> paramMap = new HashMap<>();
	private Map<String, List<Type>> returnMap = new HashMap<>();

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

	private static final String PC = "$pc";
	private static final String BLOCK_NAME = "$START";
	private static final String BASE_LABEL = "$BASE";

	private static final Integer BASE_MEMORY_LOCATION = 0;
	private static final Integer BASE_MEMORY_VALUE = 4;
	private static final Integer BASE_MEMORY_INCORRECT_VALUE = 0;

	private static final String DEFAULT_LABEL_NAME = "WASMLABEL";
	private static final String DEFAULT_VAR_NAME = "$WASMVAR";

	private static final int START_MEMORY = 1024;

	private PrintStream output;
	private WastFactory factory;
	private Map<String, Integer> labelMap = new HashMap<>();
	private WyilFile.FunctionOrMethod currentMethod;
	private int labelNum = 0;

	//Related to the Default names.
	private int wasmLabelNumber = 0;
	private int wasmVarNumber = 0;

	public WasmFileWriter(PrintStream output, WastFactory factory) {
		this.output = output;
		this.factory = factory;
	}
	
	public void write(WyilFile file) throws IOException {
		List<ModuleElement.Export> exports = new ArrayList<>();
		List<Function> functions = new ArrayList<>();
		for(WyilFile.FunctionOrMethod d : file.functionOrMethods()) {
			currentMethod = d;
			functions.add(write(d));
			exports.add(factory.createExport("\""+d.name()+"\"",factory.createVar("$"+d.name())));
			paramMap.put(d.name(), d.type().params());
			returnMap.put(d.name(), d.type().returns());

		}

		ModuleElement.Memory memory = factory.createMemory(START_MEMORY, null, null);

		Module module = factory.createModule(null,functions,null,exports,null, memory,null);



		//Needs to create file
		PrintStream out = new PrintStream(new FileOutputStream("wasm/test.wast"));
		BufferedOutputStream out2 = new BufferedOutputStream(new FileOutputStream("wasm/test2.wast"));
		module.write(out2, 0);
		out2.close();
		out.close();

	}

	/**
	 * Translate a function or method into WebAssembly
	 *
	 * @param d
	 * @throws IOException
	 */
	private Function write(WyilFile.FunctionOrMethod d) throws IOException {
		labelNum = 0; //FIXME: Make this case not needed some how.
		labelMap = new HashMap<>(); //TODO: Remove this it may be not needed.
		//indent(indent);

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

		List<FunctionElement.Local> locals = new ArrayList<>();
		if (d.body() != null) {
			locals = writeVariable(d.body(),variableList);
		}

		locals.add(factory.createLocal(PC, factory.createExprType(Expr.INT)));
		List<Expr> mainBlock = new ArrayList<>();
		mainBlock.add(
				factory.createSetLocal(
						factory.createVar(PC),
						factory.createConst(
								factory.createExprType(Expr.INT),
								factory.createValue(0)
						)
				)
		);

		output.print(d.name());
		output.print(" ");
		output.println(d.type());
		List<Expr> exprs = null;
		if(d.body() != null) {
			reset();//TODO: Find a better way to go about this.
			exprs = write(d.body(), 0);
			reset();
		}

		//TODO think of a better way to do this.
		//FIXME: It might be better to return -1.
		if (exprs != null && !currentMethod.type().returns().isEmpty()) {
			exprs.add(factory.createUnreachable());
		}
		//indent(indent);
		output.println();


		System.out.println(exprs);
		mainBlock.add(factory.createLoop(null, BLOCK_NAME, exprs));


		return factory.createFunction("$"+d.name(), null, params, result, locals, mainBlock);
	}

	private List<FunctionElement.Param> writeParams(List<Type> params, List<Integer> variableList){
		int i = 0;
		List<FunctionElement.Param> pars = new ArrayList<>();
		for (Type param: params) {
			variableList.add(i);
			pars.add(factory.createParam("$"+i++, factory.createExprType(getType(param))));
		}
		return pars;
	}

	private FunctionElement.Result writeReturns(List<Type> returns, List<Integer> variableList){
		if (!returns.isEmpty()) {
			return factory.createResult(factory.createExprType(getType(returns.get(0))));
		} else {
			return null; //Todo: Think of a more elegent way of handling this.
		}
	}

	private List<Expr> write(CodeBlock c, int caseCount) {
		List<Expr> cases = new ArrayList<>();
		List<Expr> exprs = new ArrayList<>();

		Expr.If caseIf = createCase(exprs, labelMap.get(BASE_LABEL));
		cases.add(caseIf);
		exprs = caseIf.getThenExprs();

//		output.println(labelMap);
		Code prev = null;
		List<Code> codes = new ArrayList<>(c.bytecodes());
		for(int i = 0; i < codes.size(); i++) {
			Code bytecode = codes.get(i);
			//indent(indent);
			if (bytecode instanceof Codes.ArrayGenerator){
			} else if (bytecode instanceof Codes.Assert) {
				//write((Codes.Assert) bytecode).forEach(exprs::add);
				codes.remove(bytecode);
				Codes.Assert a = (Codes.Assert) bytecode;
				int temp = i;
				for (int ii = 0; temp < codes.size() && ii < a.bytecodes().size(); temp++, ii++) {
					codes.add(temp, a.bytecodes().get(ii));
				}
				i--;
				continue;
			} else if (bytecode instanceof Codes.Assign) {
				exprs.add(write((Codes.Assign) bytecode));
			} else if (bytecode instanceof Codes.Assume) {
				//write((Codes.Assert) bytecode).forEach(exprs::add);
				codes.remove(bytecode);
				Codes.Assume a = (Codes.Assume) bytecode;
				int temp = i;
				for (int ii = 0; temp < codes.size() && ii < a.bytecodes().size(); temp++, ii++) {
					codes.add(temp, a.bytecodes().get(ii));
				}
				i--;
				continue;
			} else if (bytecode instanceof Codes.BinaryOperator) {
				Expr expr = write((Codes.BinaryOperator) bytecode);
				if (expr != null) {
					exprs.add(expr);
				}
			} else if (bytecode instanceof Codes.Const) {
				exprs.add(write((Codes.Const) bytecode));
			} else if (bytecode instanceof Codes.Convert) {
			} else if (bytecode instanceof Codes.Debug) {
			} else if (bytecode instanceof Codes.Dereference) {
			} else if (bytecode instanceof Codes.Fail) {
				exprs.add(factory.createUnreachable());
			} else if (bytecode instanceof Codes.FieldLoad) {
			} else if (bytecode instanceof Codes.Goto) {
				write((Codes.Goto) bytecode).forEach(exprs::add);
			} else if (bytecode instanceof Codes.If) { //TODO: Make is so that it checks a list appropriately.
				exprs.add(write((Codes.If) bytecode));
			} else if (bytecode instanceof Codes.IfIs) {
			} else if (bytecode instanceof Codes.IndexOf) {
				write((Codes.IndexOf) bytecode).forEach(exprs::add);
			} else if (bytecode instanceof Codes.IndirectInvoke) {
			} else if (bytecode instanceof Codes.Invariant) {
			} else if (bytecode instanceof Codes.Invert) {
			} else if (bytecode instanceof Codes.Invoke) {
				exprs.add(write((Codes.Invoke) bytecode));
			} else if (bytecode instanceof Codes.Label) {
				if (!(prev instanceof Codes.Goto) &&
						!(prev instanceof Codes.Fail &&
						!(prev instanceof Codes.Return))) { //TODO: Make appropriate method for this.
					exprs.add(
							factory.createSetLocal(
									factory.createVar(PC),
									factory.createBinOp(
											factory.createExprType(Expr.INT),
											Expr.add,
											factory.createGetLocal(factory.createVar(PC)),
											factory.createConst(
													factory.createExprType(Expr.INT),
													factory.createValue(1)
											)
									)
							)
					);
					exprs.add(
							factory.createBr(
									factory.createVar(BLOCK_NAME),
									null
							)
					);
				}
				exprs = new ArrayList<>();
				caseIf = createCase(exprs, labelMap.get(((Codes.Label) bytecode).label));
				cases.add(caseIf);
				exprs = caseIf.getThenExprs();
			} else if (bytecode instanceof Codes.Lambda) {
			} else if (bytecode instanceof Codes.LengthOf) {
				exprs.add(write((Codes.LengthOf) bytecode));
			} else if (bytecode instanceof Codes.Loop) {
				//write((Codes.Assert) bytecode).forEach(exprs::add);
				codes.remove(bytecode);
				Codes.Loop a = (Codes.Loop) bytecode;
				String label = getLabel();
				codes.add(i, Codes.Label(label));
				int temp = i+1;
				for (int ii = 0; temp < codes.size() && ii < a.bytecodes().size(); temp++, ii++) {
					codes.add(temp, a.bytecodes().get(ii));
				}
				i--;
				codes.add(temp, Codes.Goto(label));
				continue;
			} else if (bytecode instanceof Codes.Move) {
			} else if (bytecode instanceof Codes.NewArray) {
				write((Codes.NewArray) bytecode).forEach(exprs::add);
			} else if (bytecode instanceof Codes.NewObject) {
			} else if (bytecode instanceof Codes.NewRecord) {
			} else if (bytecode instanceof Codes.Nop) {
			} else if (bytecode instanceof Codes.Not) {
			} else if (bytecode instanceof Codes.Quantify) {
			} else if (bytecode instanceof Codes.Return) {
				Expr expr = write((Codes.Return) bytecode);
				if (expr != null) {
					exprs.add(expr);
				}
			} else if (bytecode instanceof Codes.Switch) {
			} else if (bytecode instanceof Codes.UnaryOperator) {
			} else if (bytecode instanceof Codes.Update) {
				exprs.add(write((Codes.Update) bytecode));
			} else if (bytecode instanceof Codes.Void) {
			} else if (bytecode == null) { //TODO: Create a better way of doing this.
				exprs.add(factory.createBr(factory.createVar(BLOCK_NAME), null));
			}
			prev = bytecode;
			//Map<String, List<Expr>> labelMap = mapLabels(c.bytecodes());
			//write(bytecode, caseCount).forEach(exprs::add);

			if(bytecode instanceof Code.Compound) {
				output.println("\t" + bytecode.getClass().getName() + " {");
				write((CodeBlock) bytecode, caseCount);
				output.println("}");
			}
			 else {
				output.println("\t" + bytecode);
			}

		}
		return cases;
	}

	private Expr write(Codes.Update c) {
//		System.out.println(c);
		System.out.println(c.key(0));
		System.out.println(c.target(0));
		System.out.println(c.operand(1));

		return factory.createStore(
				factory.createExprType(Expr.INT),
				null,
				null,
				factory.createBinOp(
						factory.createExprType(Expr.INT),
						Expr.add,
						factory.createGetLocal(
								factory.createVar("$"+c.target(0))
						),
						factory.createBinOp(
								factory.createExprType(Expr.INT),
								Expr.mul,
								factory.createConst(
										factory.createExprType(Expr.INT),
										factory.createValue(4)
								),
								factory.createBinOp(
										factory.createExprType(Expr.INT),
										Expr.add,
										factory.createConst(
												factory.createExprType(Expr.INT),
												factory.createValue(1)
										),
										factory.createGetLocal(
												factory.createVar("$"+c.key(0))
										)
								)
						)
				),
				factory.createGetLocal(
						factory.createVar("$"+c.operand(1))
				)
		);

	}

	private String getLabel() {
		String label = DEFAULT_LABEL_NAME +(wasmLabelNumber++);
		//labelMap.put(label, labelNum++);
		return label;
	}

	private String getVar() {
		String var = DEFAULT_VAR_NAME +(wasmVarNumber++);
		return var;
	}

	private Expr write(Codes.LengthOf bytecode) {
		return factory.createSetLocal(
				factory.createVar("$"+bytecode.target(0)),
				factory.createLoad(
						factory.createExprType(Expr.INT),
						null,
						null,
						null,
						factory.createGetLocal(
								factory.createVar("$"+bytecode.operand(0))
						) //TODO: Fix the implemtaion fo loops.
				)
		);
	}

	private List<Expr> write(Codes.IndexOf c) {
		List<Expr> exprs = new ArrayList<>();

		System.out.println(c.operand(0));
		System.out.println(c.operand(1));

		exprs.add(
				factory.createSetLocal(
						factory.createVar("$"+c.target(0)),
						factory.createLoad(
							factory.createExprType(Expr.INT), //TODO: extend for more types.
							null,
							null,
							null,
							factory.createBinOp(
									factory.createExprType(Expr.INT), //TODO: Extend for more types.
									Expr.add,
									factory.createGetLocal(factory.createVar("$"+c.operand(0))),
									factory.createBinOp(
											factory.createExprType(Expr.INT), //TODO: Extend for more types.
											Expr.mul,
											factory.createConst(
													factory.createExprType(Expr.INT),
													factory.createValue(4)
											),
											factory.createBinOp(
													factory.createExprType(getType(c.type(0).element())),
													Expr.add,
													factory.createGetLocal(
															factory.createVar("$"+c.operand(1))
													),
													factory.createConst(
															factory.createExprType(getType(c.type(0).element())),
															factory.createValue(1))
											)
									)
							)
						)
				)
		);

		return exprs;
	}

	private List<Expr> write(Codes.NewArray bytecode) {
		List<Expr> exprs = new ArrayList<>();

		List<Expr> then = new ArrayList<>();

		then.add(
				factory.createStore(
						factory.createExprType(Expr.INT),
						null,
						null,
						factory.createConst(factory.createExprType(Expr.INT), factory.createValue(BASE_MEMORY_LOCATION)),
						factory.createConst(factory.createExprType(Expr.INT), factory.createValue(BASE_MEMORY_VALUE))
				)
		);

		exprs.add(
				factory.createIf(
						factory.createRelOp(
								factory.createExprType(Expr.INT),
								Expr.EQ,
								factory.createConst(
										factory.createExprType(Expr.INT),
										factory.createValue(BASE_MEMORY_INCORRECT_VALUE)
								),
								factory.createLoad(
										factory.createExprType(Expr.INT),
										null,
										null,
										null,
										factory.createConst(
												factory.createExprType(Expr.INT),
												factory.createValue(BASE_MEMORY_LOCATION)
										)
								)
						),
						null,
						then,
						null,
						null
				)
		);

		//TODO: make memory grow if the array make the size to big.

		exprs.add( //Sets the local var to the pointer to array location.
				factory.createSetLocal(
						factory.createVar("$"+bytecode.target(0)),
						factory.createLoad(
								factory.createExprType(Expr.INT),
								null,
								null,
								null,
								factory.createConst(
										factory.createExprType(Expr.INT),
										factory.createValue(BASE_MEMORY_LOCATION)
								)
						)
				)
		);

		System.out.println(bytecode.operands().length);

		//Loading in the length
		exprs.add(
			factory.createStore(
					factory.createExprType(Expr.INT),
					null,
					null,
					factory.createLoad(
							factory.createExprType(Expr.INT),
							null,
							null,
							null,
							factory.createConst(
									factory.createExprType(Expr.INT),
									factory.createValue(BASE_MEMORY_LOCATION)
							)
					),
					factory.createConst(
							factory.createExprType(Expr.INT),
							factory.createValue(bytecode.operands().length)
					)
			)
		);

		for (int i = 0; i < bytecode.operands().length; i++) {
			exprs.add(
					factory.createStore(
							factory.createExprType(Expr.INT),
							(i+1)*4,
							null,
							factory.createLoad(
									factory.createExprType(Expr.INT),
									null,
									null,
									null,
									factory.createConst(
											factory.createExprType(Expr.INT),
											factory.createValue(BASE_MEMORY_LOCATION)
									)
							),
							factory.createGetLocal(
									factory.createVar("$"+bytecode.operand(i)) // FIXME: 5/05/16 Could be problems here.
							)
					)
			);
		}

		exprs.add( //Sets the local var to the pointer to array location.
				factory.createStore(
						factory.createExprType(Expr.INT),
						null,
						null,
						factory.createConst(factory.createExprType(Expr.INT), factory.createValue(BASE_MEMORY_LOCATION)),
						factory.createBinOp(
								factory.createExprType(Expr.INT),
								Expr.add,
								factory.createLoad(
										factory.createExprType(Expr.INT),
										null,
										null,
										null,
										factory.createConst(factory.createExprType(Expr.INT), factory.createValue(BASE_MEMORY_LOCATION))
								),
								factory.createConst(factory.createExprType(Expr.INT), factory.createValue((bytecode.operands().length+1)*4))

						)
				)
		);


		return exprs;
	}

	private Expr write(Codes.Assign c) {
		if (isArray(c.type(0))) {
			return writeAssignArray(c);
		} else {
			return factory.createSetLocal(
					factory.createVar(
							"$" + c.target(0)),
					factory.createGetLocal(
							factory.createVar("$" + c.operand(0)
							)
					)
			);
		}
	}

	private Expr writeAssignArray(Codes.Assign c) {
		String label = getLabel();
		String var = getVar();

		System.out.println(c.operand(0));
		System.out.println(c.target(0));

		List<Expr> then = new ArrayList<>();


		Expr.Store store = factory.createStore(
				factory.createExprType(Expr.INT),
				null,
				null,
				factory.createBinOp(
						factory.createExprType(Expr.INT),
						Expr.add,
						factory.createGetLocal(
								factory.createVar("$"+c.target(0))
						),
						factory.createBinOp(
								factory.createExprType(Expr.INT),
								Expr.mul,
								factory.createGetLocal(
										factory.createVar(var)
								),
								factory.createConst(
										factory.createExprType(Expr.INT),
										factory.createValue(4)
								)
						)
				),
				factory.createLoad(
						factory.createExprType(Expr.INT),
						null,
						null,
						null,
						factory.createBinOp(
								factory.createExprType(Expr.INT),
								Expr.add,
								factory.createGetLocal(
										factory.createVar("$"+c.operand(0))
								),
								factory.createBinOp(
										factory.createExprType(Expr.INT),
										Expr.mul,
										factory.createGetLocal(
												factory.createVar(var)
										),
										factory.createConst(
												factory.createExprType(Expr.INT),
												factory.createValue(4)
										)
								)
						)

				)
		);

		then.add(store);

		Expr.SetLocal increament = factory.createSetLocal(
				factory.createVar(var),
				factory.createBinOp(
						factory.createExprType(Expr.INT),
						Expr.add,
						factory.createGetLocal(
								factory.createVar(var)
						),
						factory.createConst(
								factory.createExprType(Expr.INT),
								factory.createValue(1)
						)
				)
		);

		then.add(increament);

		List<Expr> cont = new ArrayList<>();

		cont.add(factory.createBr(factory.createVar("$"+label), null));

		Expr.If loopContinue = factory.createIf(
				factory.createRelOp(
						factory.createExprType(Expr.INT),
						Expr.LE,
						factory.createGetLocal(
								factory.createVar(var)
						),
						factory.createLoad(
								factory.createExprType(Expr.INT),
								null,
								null,
								null,
								factory.createGetLocal(
										factory.createVar("$"+c.target(0))
								)
						)
				),
				null,
				cont,
				null,
				null
		);

		then.add(loopContinue);


		Expr.SetLocal creatingVar = factory.createSetLocal(
				factory.createVar(var),
				factory.createConst(
						factory.createExprType(Expr.INT),
						factory.createValue(1)
				)
		);

		Expr.Loop loop = factory.createLoop(null, "$"+label, then);

		then = new ArrayList<>();

		Expr.SetLocal setLocal = factory.createSetLocal(
				factory.createVar("$"+c.target(0)),
				factory.createLoad(
						factory.createExprType(Expr.INT),
						null,
						null,
						null,
						factory.createConst(
								factory.createExprType(Expr.INT),
								factory.createValue(BASE_MEMORY_LOCATION)
						)
				)
		);

		Expr.Store storeLength = factory.createStore(
				factory.createExprType(Expr.INT),
				null,
				null,
				factory.createGetLocal(
						factory.createVar("$"+c.target(0))
				),
				factory.createLoad(
						factory.createExprType(Expr.INT),
						null,
						null,
						null,
						factory.createGetLocal(
								factory.createVar("$"+c.operand(0))
						)
				)
		);

		then.add(setLocal);

		then.add(creatingVar);
		then.add(storeLength);
		then.add(loop);

		then.add( //Sets the local var to the pointer to array location.
				factory.createStore(
						factory.createExprType(Expr.INT),
						null,
						null,
						factory.createConst(factory.createExprType(Expr.INT), factory.createValue(BASE_MEMORY_LOCATION)),
						factory.createBinOp(
								factory.createExprType(Expr.INT),
								Expr.add,
								factory.createLoad(
										factory.createExprType(Expr.INT),
										null,
										null,
										null,
										factory.createConst(factory.createExprType(Expr.INT), factory.createValue(BASE_MEMORY_LOCATION))
								),
								factory.createBinOp(
										factory.createExprType(Expr.INT),
										Expr.add,
										factory.createLoad(
												factory.createExprType(Expr.INT),
												null,
												null,
												null,
												factory.createConst(
														factory.createExprType(Expr.INT),
														factory.createValue(BASE_MEMORY_LOCATION)
												)
										),
										factory.createBinOp(
												factory.createExprType(Expr.INT),
												Expr.mul,
												factory.createConst(
														factory.createExprType(Expr.INT),
														factory.createValue(4)
												),
												factory.createBinOp(
														factory.createExprType(Expr.INT),
														Expr.add,
														factory.createLoad(
																factory.createExprType(Expr.INT),
																null,
																null,
																null,
																factory.createGetLocal(
																		factory.createVar("$"+c.target(0))
																)
														),
														factory.createConst(
																factory.createExprType(Expr.INT),
																factory.createValue(1)
														)
												)
										)
								)
						)
				)
		);


		return factory.createBlock(null, then);
	}

	private Expr write(Codes.If c) {
		if (isArray(c.type(0))){
			return writeArrayIf(c);
		} else {
			List<Expr> then = new ArrayList<>();
			then.add(
					factory.createSetLocal(
							factory.createVar(PC),
							factory.createConst(
									factory.createExprType(Expr.INT),
									factory.createValue(labelMap.get(c.target))
							)
					)
			);
			then.add(factory.createBr(
					factory.createVar(BLOCK_NAME),
					null)); //TODO: Find a better way to fix up a branching statment.
			return factory.createIf(factory.createRelOp(
					factory.createExprType(Expr.INT),
					getOp(c.opcode()),
					factory.createGetLocal(factory.createVar("$" + c.operand(0))),
					factory.createGetLocal(factory.createVar("$" + c.operand(1)))
			), null, then, null, null);
		}
	}

	private Expr writeArrayIf(Codes.If c) {
		String var = getVar();
		String label = getLabel();

		List<Expr> cases = new ArrayList<>();

		List<Expr> then = new ArrayList<>();

		then.add(
				factory.createSetLocal(
						factory.createVar(PC),
						factory.createConst(
								factory.createExprType(Expr.INT),
								factory.createValue(labelMap.get(c.target))
						)
				)
		);
		then.add(factory.createBr(
				factory.createVar(BLOCK_NAME),
				null));

		Expr.If secondIf = factory.createIf(
				factory.createRelOp(
						factory.createExprType(Expr.INT), //TODO: Fix this to work with more types.
						Expr.EQ,
						factory.createGetLocal(
								factory.createVar(var)
						),
						factory.createLoad(
								factory.createExprType(Expr.INT),
								null,
								null,
								null,
								factory.createGetLocal(
										factory.createVar("$"+c.operand(0))
								)
						)
				),
				null,
				then,
				null,
				null
		);

		then = new ArrayList<>();

		then.add(secondIf);

		Expr.SetLocal increament = factory.createSetLocal(
				factory.createVar(var),
				factory.createBinOp(
						factory.createExprType(Expr.INT),
						Expr.add,
						factory.createGetLocal(
								factory.createVar(var)
						),
						factory.createConst(
								factory.createExprType(Expr.INT),
								factory.createValue(1)
						)
				)
		);

		then.add(increament);

		List<Expr> cont = new ArrayList<>();

		cont.add(factory.createBr(factory.createVar("$"+label), null));

		Expr.If loopContinue = factory.createIf(
				factory.createRelOp(
						factory.createExprType(Expr.INT),
						Expr.LE,
						factory.createGetLocal(
								factory.createVar(var)
						),
						factory.createLoad(
								factory.createExprType(Expr.INT),
								null,
								null,
								null,
								factory.createGetLocal(
										factory.createVar("$"+c.operand(0))
								)
						)
				),
				null,
				cont,
				null,
				null
		);

		then.add(loopContinue);

		Expr.If firstIf = factory.createIf(
				factory.createRelOp(
						factory.createExprType(Expr.INT),
						getOp(c.opcode()),
						factory.createLoad(
								factory.createExprType(Expr.INT),
								null,
								null,
								null,
								factory.createBinOp(
										factory.createExprType(Expr.INT),
										Expr.add,
										factory.createGetLocal(
												factory.createVar("$"+c.operand(0))
										),
										factory.createBinOp(
												factory.createExprType(Expr.INT),
												Expr.mul,
												factory.createGetLocal(
														factory.createVar(var)
												),
												factory.createConst(
														factory.createExprType(Expr.INT),
														factory.createValue(4)
												)
										)
								)
						),
						factory.createLoad(
								factory.createExprType(Expr.INT),
								null,
								null,
								null,
								factory.createBinOp(
										factory.createExprType(Expr.INT),
										Expr.add,
										factory.createGetLocal(
												factory.createVar("$"+c.operand(1))
										),
										factory.createBinOp(
												factory.createExprType(Expr.INT),
												Expr.mul,
												factory.createGetLocal(
														factory.createVar(var)
												),
												factory.createConst(
														factory.createExprType(Expr.INT),
														factory.createValue(4)
												)
										)
								)
						)
				),
				null,
				then,
				null,
				null
		);

		then = new ArrayList<>();
		then.add(firstIf);


		Expr.SetLocal creatingVar = factory.createSetLocal(
				factory.createVar(var),
				factory.createConst(
						factory.createExprType(Expr.INT),
						factory.createValue(1)
				)
		);

		Expr.Loop loop = factory.createLoop(null, "$"+label, then);

		then = new ArrayList<>();

		then.add(creatingVar);
		then.add(loop);

		cases.add(
				factory.createIf(
						factory.createRelOp(
								factory.createExprType(Expr.INT),
								getOp(c.opcode()),
								factory.createLoad(
										factory.createExprType(Expr.INT),
										null,
										null,
										null,
										factory.createGetLocal(
												factory.createVar("$"+c.operand(0))
										)
								),
								factory.createLoad(
										factory.createExprType(Expr.INT),
										null,
										null,
										null,
										factory.createGetLocal(
												factory.createVar("$"+c.operand(1))
										)
								)
						),
						null,
						then,
						null,
						null
				)
		);

		return factory.createBlock(null, cases);
	}


	private List<Expr> write(Codes.Goto c) {
		List<Expr> exprs = new ArrayList<>();

		exprs.add(
				factory.createSetLocal(
						factory.createVar(PC),
						factory.createConst(
								factory.createExprType(Expr.INT),
								factory.createValue(labelMap.get(c.target))
						)
				)
		); // Set the PC to be the label to go to.

		exprs.add(factory.createBr(factory.createVar(BLOCK_NAME), null));
		//Branch to the start so the the label case is selected.

		return exprs;
	}

	private Expr.If createCase (List<Expr> exprs, int c) {
		return factory.createIf(
				factory.createRelOp(
						factory.createExprType(Expr.INT),
						Expr.EQ,
						factory.createGetLocal(factory.createVar(PC)),
						factory.createConst(factory.createExprType(Expr.INT),
								factory.createValue(c)
						)
				),
				null, // Then Label
				exprs, // Then List
				null, // Else Label
				null // Else List
		);
	}


	private Expr write(Codes.Const c) {
		//TODO: Add in some code for dealing with a constant array.
		//Should include alot of code form new array.
		//Should rather than using store loaded vars it should store constants
		//But other than that the same code.
		if (c.constant.type().equals(Type.Array(Type.T_INT, true))) {
			return writeConstantArray(c);
		}
//		output.println(c.constant.type());
//		output.println(Type.T_ARRAY_ANY);
		return factory.createSetLocal(factory.createVar("$"+c.target()),
				factory.createConst(writeConstantType(c.constant.type()), writeConstantValue(c.constant)));
	}

	private Expr writeConstantArray(Codes.Const c) {
		List<Expr> exprs = new ArrayList<>();

		List<Expr> then = new ArrayList<>();

		then.add(
				factory.createStore(
						factory.createExprType(Expr.INT),
						null,
						null,
						factory.createConst(factory.createExprType(Expr.INT), factory.createValue(BASE_MEMORY_LOCATION)),
						factory.createConst(factory.createExprType(Expr.INT), factory.createValue(BASE_MEMORY_VALUE))
				)
		);

		exprs.add(
				factory.createIf(
						factory.createRelOp(
								factory.createExprType(Expr.INT),
								Expr.EQ,
								factory.createConst(
										factory.createExprType(Expr.INT),
										factory.createValue(BASE_MEMORY_INCORRECT_VALUE)
								),
								factory.createLoad(
										factory.createExprType(Expr.INT),
										null,
										null,
										null,
										factory.createConst(
												factory.createExprType(Expr.INT),
												factory.createValue(BASE_MEMORY_LOCATION)
										)
								)
						),
						null,
						then,
						null,
						null
				)
		);

		//TODO: make memory grow if the array make the size to big.

		exprs.add( //Sets the local var to the pointer to array location.
				factory.createSetLocal(
						factory.createVar("$"+c.target(0)),
						factory.createLoad(
								factory.createExprType(Expr.INT),
								null,
								null,
								null,
								factory.createConst(
										factory.createExprType(Expr.INT),
										factory.createValue(BASE_MEMORY_LOCATION)
								)
						)
				)
		);

//		System.out.println(c.constant);
		Constant.Array array = (Constant.Array) c.constant;
//		System.out.println(array.values.size());
//		System.out.println(array.values.get(0).toString());


		exprs.add(
				factory.createStore(
						factory.createExprType(Expr.INT),
						null,
						null,
						factory.createLoad(
								factory.createExprType(Expr.INT),
								null,
								null,
								null,
								factory.createConst(
										factory.createExprType(Expr.INT),
										factory.createValue(BASE_MEMORY_LOCATION)
								)
						),
						factory.createConst(
								factory.createExprType(Expr.INT),
								factory.createValue(array.values.size())
						)
				)
		);

		for (int i = 0; i < array.values.size(); i++) {
			exprs.add(
					factory.createStore(
							factory.createExprType(Expr.INT),
							(i+1)*4,
							null,
							factory.createLoad(
									factory.createExprType(Expr.INT),
									null,
									null,
									null,
									factory.createConst(
											factory.createExprType(Expr.INT),
											factory.createValue(BASE_MEMORY_LOCATION)
									)
							),
							factory.createConst(
									factory.createExprType(Expr.INT),
									factory.createValue(new Integer(array.values.get(i).toString())) //TODO: Fix this up.
							)
					)
			);
		}

		exprs.add( //Sets the local var to the pointer to array location.
				factory.createStore(
						factory.createExprType(Expr.INT),
						null,
						null,
						factory.createConst(factory.createExprType(Expr.INT), factory.createValue(BASE_MEMORY_LOCATION)),
						factory.createBinOp(
								factory.createExprType(Expr.INT),
								Expr.add,
								factory.createLoad(
										factory.createExprType(Expr.INT),
										null,
										null,
										null,
										factory.createConst(factory.createExprType(Expr.INT), factory.createValue(BASE_MEMORY_LOCATION))
								),
								factory.createConst(factory.createExprType(Expr.INT), factory.createValue((array.values.size()+1)*4))

						)
				)
		);

		return factory.createBlock(null, exprs);
	}

	private ExprElement.Type writeConstantType(Type type) {
		System.out.println(type);
		if (type.equals(Type.T_INT)) {
			return factory.createExprType(Expr.INT);
		} else if (type.equals(Type.T_BOOL)) {
			return factory.createExprType(Expr.INT);
		} else if (type.equals(Type.T_ARRAY_ANY)) {
			//TODO: Add in the create a constant array;
//			System.out.println("Should have made it here.");
			return factory.createExprType(Expr.INT);
		} else if (type.equals(Type.Array(Type.T_INT, true))) {}
		//Todo throw error
//		System.out.println(type);
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

	private Expr write(Codes.BinaryOperator c) {
		//TODO: add the ability to have more targets
		return factory.createSetLocal(
				factory.createVar("$"+c.target(0)),
				factory.createBinOp(factory.createExprType(getType(c.type(0))),
						getOp(c.opcode()),
						factory.createGetLocal(
								factory.createVar("$"+c.operand(0))),
						factory.createGetLocal(
								factory.createVar("$"+c.operand(1)))));
	}

	private Expr write(Codes.Return c) {
		if (c.operands().length == 0){
			return null;//TODO: add something in here for if the based on return values needed.
		} else {
			return factory.createReturn(factory.createGetLocal(
					factory.createVar("$"+c.operand(0))));
		}
	}

	private Expr write(Codes.Invoke c) {//TODO:Make it so that functions can call functions from other files.
		//List<Type> parameterTypes = c.type(0).params();
		List<Expr> exprs = new ArrayList<>();

		for (int operand: c.operands()) {
			exprs.add(factory.createGetLocal(
				factory.createVar("$"+operand)
			));
		}
		if (c.targets().length <= 0 ) {
			return factory.createCall(
					factory.createVar("$" + c.name.name()),
					exprs
			);
		} else {
			return factory.createSetLocal(
					factory.createVar("$" + c.target(0)),
					factory.createCall(
							factory.createVar("$" + c.name.name()),
							exprs
					)
			);
		}
	}

	//TODO: Remove un-need calls.
	private List<FunctionElement.Local> writeVariable(CodeBlock d, List<Integer> variableList) {
		List<FunctionElement.Local> locals = new ArrayList<>();
		labelMap.put(BASE_LABEL, labelNum++);
		int oldWasmLabelNumber = wasmLabelNumber;
		List<Code> codes = new ArrayList<>(d.bytecodes());
		for(int i = 0; i < codes.size(); i++) {
			Code bytecode = codes.get(i);
			if (bytecode instanceof Codes.ArrayGenerator) {
			} else if (bytecode instanceof Codes.Assert) {
				List<FunctionElement.Local> local = writeVariable((Codes.Assert) bytecode, variableList);
				local.forEach(locals::add);
				codes.remove(bytecode);
				Codes.Assert a = (Codes.Assert) bytecode;
				int temp = i;
				for (int ii = 0; temp < codes.size() && ii < a.bytecodes().size(); temp++, ii++) {
					codes.add(temp, a.bytecodes().get(ii));
				}
				i--;
			} else if (bytecode instanceof Codes.Assign) {
//				System.out.println(locals.size());
//				System.out.println(variableList);
				writeVariable((Codes.Assign) bytecode, variableList).forEach(locals::add);
//				System.out.println(variableList);
//				System.out.println(locals.size());
			} else if (bytecode instanceof Codes.Assume) {
//				List<FunctionElement.Local> local = writeVariable((Codes.Assume) bytecode, variableList);
//				local.forEach(locals::add);
				codes.remove(bytecode);
				Codes.Assume a = (Codes.Assume) bytecode;
				int temp = i;
				for (int ii = 0; temp < codes.size() && ii < a.bytecodes().size(); temp++, ii++) {
					codes.add(temp, a.bytecodes().get(ii));
				}
				i--;
			} else if (bytecode instanceof Codes.BinaryOperator) {
				addToLocal(locals, writeVariable((Codes.BinaryOperator) bytecode, variableList));
			} else if (bytecode instanceof Codes.Const) {
				addToLocal(locals, writeVariable((Codes.Const) bytecode, variableList));
			} else if (bytecode instanceof Codes.Convert) {
			} else if (bytecode instanceof Codes.Debug) {
			} else if (bytecode instanceof Codes.Dereference) {
			} else if (bytecode instanceof Codes.Fail) {
			} else if (bytecode instanceof Codes.FieldLoad) {
			} else if (bytecode instanceof Codes.Goto) {
			} else if (bytecode instanceof Codes.If) {
				addToLocal(locals, writeVariable((Codes.If) bytecode, variableList));
			} else if (bytecode instanceof Codes.IfIs) {
			} else if (bytecode instanceof Codes.IndexOf) {
				addToLocal(locals, writeVariable((Codes.IndexOf) bytecode, variableList));
			} else if (bytecode instanceof Codes.IndirectInvoke) {
			} else if (bytecode instanceof Codes.Invariant) {
			} else if (bytecode instanceof Codes.Invert) {
			} else if (bytecode instanceof Codes.Invoke) {
				addToLocal(locals,writeVariable((Codes.Invoke) bytecode, variableList));
			} else if (bytecode instanceof Codes.Label) {
				if (!labelMap.containsKey(((Codes.Label) bytecode).label)) {
					labelMap.put(((Codes.Label) bytecode).label, labelNum++);
				}
			} else if (bytecode instanceof Codes.Lambda) {
			} else if (bytecode instanceof Codes.LengthOf) {
				addToLocal(locals,writeVariable((Codes.LengthOf) bytecode, variableList));
			} else if (bytecode instanceof Codes.Loop) {
//				List<FunctionElement.Local> local = writeVariable((Codes.Loop) bytecode, variableList);
//				local.forEach(locals::add)
				codes.remove(bytecode);
				Codes.Loop a = (Codes.Loop) bytecode;
				String label = getLabel();
				codes.add(i, Codes.Label(label));
				int temp = i+1;
				for (int ii = 0; temp < codes.size() && ii < a.bytecodes().size(); temp++, ii++) {
					codes.add(temp, a.bytecodes().get(ii));
				}
				i--;
				codes.add(temp, Codes.Goto(label));

			} else if (bytecode instanceof Codes.Move) {
			} else if (bytecode instanceof Codes.NewArray) {
				addToLocal(locals, writeVariable((Codes.NewArray) bytecode, variableList));
			} else if (bytecode instanceof Codes.NewObject) {
			} else if (bytecode instanceof Codes.NewRecord) {
			} else if (bytecode instanceof Codes.Nop) {
			} else if (bytecode instanceof Codes.Not) {
			} else if (bytecode instanceof Codes.Quantify) {
			} else if (bytecode instanceof Codes.Switch) {
			} else if (bytecode instanceof Codes.UnaryOperator) {
			} else if (bytecode instanceof Codes.Update) {
			}
		}
		wasmLabelNumber = oldWasmLabelNumber;
		return locals;
	}


	private FunctionElement.Local writeVariable(Codes.If bytecode, List<Integer> variableList) {
		if (isArray(bytecode.type(0))) {
			getLabel(); //TODO: create a better solution than this.
			return factory.createLocal(getVar(), factory.createExprType(Expr.INT));
		}
		return null;
	}

	@Deprecated
	private List<FunctionElement.Local> writeVariable(Code bytecode, List<Integer> variableList) {
		List<FunctionElement.Local> locals = new ArrayList<>();
		return locals;
	}

	private void addToLocal(List<FunctionElement.Local> locals, FunctionElement.Local local){
		if (local != null) {
			locals.add(local);
		}
	}

	@Deprecated
	private List<FunctionElement.Local> writeVariable(Codes.Loop bytecode, List<Integer> variableList) {
		//bytecode.
		List<FunctionElement.Local> locals = new ArrayList<>();

		for (Code code: bytecode.bytecodes()) {
			writeVariable(code, variableList).forEach(locals::add);
		}
		return locals;
	}

	private FunctionElement.Local writeVariable(Codes.IndexOf bytecode, List<Integer> variableList) {
		if (variableList.contains(bytecode.target(0))){
			return null;
		}
		else {
			variableList.add(bytecode.target(0));
		}
		return factory.createLocal("$"+bytecode.target(0),
				factory.createExprType(getType(bytecode.type(0).element())));
	}

	private FunctionElement.Local writeVariable(Codes.LengthOf bytecode, List<Integer> variableList) {
		if (variableList.contains(bytecode.target(0))) {
			return null;
		} else {
			variableList.add(bytecode.target(0));
		}
		return factory.createLocal("$" + bytecode.target(0),
				factory.createExprType(Expr.INT));
	}

	private FunctionElement.Local writeVariable(Codes.NewArray bytecode, List<Integer> variableList) {
		if (variableList.contains(bytecode.target(0))) {
			return null;
		} else {
			variableList.add(bytecode.target(0));
		}
		return factory.createLocal("$" + bytecode.target(0),
				factory.createExprType(Expr.INT));
	}

	private List<FunctionElement.Local> writeVariable(Codes.Assign bytecode, List<Integer> variableList) {
		List<FunctionElement.Local> locals = new ArrayList<>();
		if (isArray(bytecode.type(0))) {
			System.out.println("Made");
			getLabel();
			locals.add(factory.createLocal(getVar(), factory.createExprType(Expr.INT)));
		}
		if (variableList.contains(bytecode.target(0))){
			return locals;
		}
		else {
			variableList.add(bytecode.target(0));
		}
//		System.out.println("Handling assign"+bytecode.target(0));
//		System.out.println(getType(bytecode.type(0)));
		locals.add(factory.createLocal("$"+bytecode.target(0),
				factory.createExprType(getType(bytecode.type(0)))));
		return locals;
	}

	@Deprecated
	private List<FunctionElement.Local> writeVariable(Codes.Assume bytecode, List<Integer> variableList) {
		List<FunctionElement.Local> locals = new ArrayList<>();

		for (Code code: bytecode.bytecodes()) {
			writeVariable(code, variableList).forEach(locals::add);
		}
		return locals;
	}

	@Deprecated
	private List<FunctionElement.Local> writeVariable(Codes.Assert bytecode, List<Integer> variableList) {
		List<FunctionElement.Local> locals = new ArrayList<>();

		for (Code code: bytecode.bytecodes()) {
			writeVariable(code, variableList).forEach(locals::add);
		}
		return locals;
	}

	/**
	 * Writes a local variable of an invocation call and adds it to the list of made vars if not there.
	 * @param bytecode
	 * @param variableList
     */
	private FunctionElement.Local writeVariable(Codes.Invoke bytecode, List<Integer> variableList) {
		if (bytecode.targets().length <= 0 || variableList.contains(bytecode.target(0))){
			return null;
		}
		else {
			variableList.add(bytecode.target(0));
		}
		List<Type> targets = bytecode.type(0).returns();
				//returnMap.get(bytecode.name.name());

		Type targetType = targets.get(0); //FIXME: Might be using incorrect target.

		return factory.createLocal("$"+bytecode.target(0),
				factory.createExprType(getType(targetType)));
	}

	/**
	 * Writes a local variable that is used in a constant call.
	 * @param bytecode
	 * @param variableList
     */
	private FunctionElement.Local writeVariable(Codes.Const bytecode, List<Integer> variableList) {
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
     */
	private FunctionElement.Local writeVariable(Codes.BinaryOperator bytecode, List<Integer> variableList){
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

	private boolean isArray(Type type) {
		if (type.equals(Type.Array(Type.T_INT, true))|| type.equals(Type.Array(Type.T_INT, false))) {
			return true;
		}
		if (type.equals(Type.Array(Type.T_BOOL, true)) || type.equals(Type.Array(Type.T_BOOL, false))) {
			return true;
		}
		if (type.equals(Type.Array(Type.T_ANY, true))|| type.equals(Type.Array(Type.T_ANY, false))) {
			return true;
		}
		if (type.equals(Type.Array(Type.T_NULL, true))|| type.equals(Type.Array(Type.T_NULL, false))) {
			return true;
		}
		return false;
	}

	/**
	 * TODO: Maby add to a map.
	 * @param t
	 * @return
     */
	private String getType(Type t){
		if (t.equals(Type.T_INT)) {
			return Expr.INT;
		} else if (t.equals(Type.T_BOOL)) {
			return BOOL;
		} else if (t.equals(Type.T_ARRAY_ANY)){
			return Expr.INT;
		} else if (t.equals(Type.T_ANY)) {
			return Expr.INT;
		}
		return "i32"; //TODO: throw a error but returning in for the mine time due to more likely value.
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
			case Code.OPCODE_rem:
				return REM;
			case Code.OPCODE_ifeq:
				return Expr.EQ;
			case Code.OPCODE_ifne:
				return Expr.NE;
			case Code.OPCODE_iflt:
				return Expr.LT;
			case Code.OPCODE_ifle:
				return Expr.LE;
			case Code.OPCODE_ifge:
				return Expr.GE;
			case Code.OPCODE_ifgt:
				return Expr.GT;
			default:
				return "";
		}
	}

	public void reset() {
		wasmLabelNumber = 0;
		wasmVarNumber = 0;
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
