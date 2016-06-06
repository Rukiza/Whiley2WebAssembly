package wywasm;

import java.io.*;
import java.util.*;
import java.util.concurrent.Exchanger;

import ast.*;
import util.WastFactory;
import wyil.io.WyilFileReader;
import wyil.lang.*;

public class WasmFileWriter {

	private static final String TYPE_VAR_NAME = "VAR_TYPE_FOR_";
	private static final String TYPE_VAR_TYPE = "i32";

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
	private Map<String, Integer> typeMap;
	private Map<String, Integer> labelMap = new HashMap<>();
	private WyilFile.FunctionOrMethod currentMethod;
	private int labelNum = 0;
	private int typeNum = 0;

	//Related to the Default names.
	private int wasmLabelNumber = 0;
	private int wasmVarNumber = 0;

	public WasmFileWriter(PrintStream output, WastFactory factory) {
		this.output = output;
		this.factory = factory;
		this.typeMap = new HashMap<>();
	}
	
	public void write(WyilFile file) throws IOException {
		initializeTypeMap();
		List<ModuleElement.Export> exports = new ArrayList<>();
		List<Function> functions = new ArrayList<>();

		for (WyilFile.Type t: file.types()) {
			System.out.println(t);
		}

		for(WyilFile.FunctionOrMethod d : file.functionOrMethods()) {
			currentMethod = d;
			functions.add(write(d));
			exports.add(factory.createExport("\""+d.name()+"\"",factory.createVar("$"+d.name())));
			paramMap.put(d.name(), d.type().params());
			returnMap.put(d.name(), d.type().returns());

		}
		functions.add(createMemoryCopyHelperFunction());

		ModuleElement.Memory memory = factory.createMemory(START_MEMORY, null, null);

		Module module = factory.createModule(null,functions,null,exports,null, memory,null);

		//Needs to create file
		PrintStream out = new PrintStream(new FileOutputStream("wasm/test.wast"));
		BufferedOutputStream out2 = new BufferedOutputStream(new FileOutputStream("wasm/test2.wast"));
		module.write(out2, 0);
		out2.close();
		out.close();
	}

	private void initializeTypeMap() {
		typeMap.put("int", typeNum++);
		typeMap.put("bool", typeNum++);
		typeMap.put("void", typeNum++);
		typeMap.put("null", typeNum++);
		typeMap.put("array", typeNum++);
		typeMap.put("record", typeNum++);

		//FIXME: add types from the declaration of file.
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
		List<FunctionElement.Local> paramLocals = new ArrayList<>();
		if (!d.type().params().isEmpty()){
			params = writeParams(d.type().params(), variableList, paramLocals);
		}

		FunctionElement.Result result = null;
		if (!d.type().returns().isEmpty()){
			result = writeReturns(d.type().returns(), variableList);
		}

		List<FunctionElement.Local> locals = new ArrayList<>();
		if (d.body() != null) {
			locals = writeVariable(d.body(),variableList);
		}

		paramLocals.forEach(locals::add);// Used for parameters

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
			exprs = write(d.body());
			reset();
		}

		//TODO think of a better way to do this.
		//FIXME: It might be better to return -1.
		if (exprs != null && !currentMethod.type().returns().isEmpty()) {
			exprs.add(factory.createUnreachable());
		}
		//indent(indent);
		output.println();


		mainBlock.add(factory.createLoop(null, BLOCK_NAME, exprs));


		return factory.createFunction("$"+d.name(), null, params, result, locals, mainBlock);
	}

	private List<FunctionElement.Param> writeParams(List<Type> params, List<Integer> variableList, List<FunctionElement.Local> locals){
		int i = 0;
		List<FunctionElement.Param> pars = new ArrayList<>();
		for (Type param: params) {
			variableList.add(i);
			locals.add(factory.createLocal(
					"$"+TYPE_VAR_NAME+i,
					factory.createExprType(TYPE_VAR_TYPE)
			));
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

	private List<Expr> write(CodeBlock c) {
		List<Expr> cases = new ArrayList<>();
		List<Expr> exprs = new ArrayList<>();

		Expr.If caseIf = createCase(exprs, labelMap.get(BASE_LABEL));
		cases.add(caseIf);
		exprs = caseIf.getThenExprs();

		Code prev = null;
		List<Code> codes = new ArrayList<>(c.bytecodes());
		for(int i = 0; i < codes.size(); i++) {
			Code bytecode = codes.get(i);
			if (bytecode instanceof Codes.ArrayGenerator){
			} else if (bytecode instanceof Codes.Assert) {
				//write((Codes.Assert) bytecode).forEach(exprs::add);
				codes.remove(bytecode);
				Codes.Assert a = (Codes.Assert) bytecode;
				int temp = i;
				flatten(temp, codes, a.bytecodes());
				i--;
				continue;
			} else if (bytecode instanceof Codes.Assign) {
				exprs.add(write((Codes.Assign) bytecode));
			} else if (bytecode instanceof Codes.Assume) {
				//write((Codes.Assert) bytecode).forEach(exprs::add);
				codes.remove(bytecode);
				Codes.Assume a = (Codes.Assume) bytecode;
				int temp = i;
				flatten(temp, codes, a.bytecodes());
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
				exprs.add(write((Codes.FieldLoad) bytecode));
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
//				System.out.println(labelMap);
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
				temp = flatten(temp, codes, a.bytecodes());
				i--;
				codes.add(temp, Codes.Goto(label));
				continue;
			} else if (bytecode instanceof Codes.Move) {
			} else if (bytecode instanceof Codes.NewArray) {
				write((Codes.NewArray) bytecode).forEach(exprs::add);
			} else if (bytecode instanceof Codes.NewObject) {
			} else if (bytecode instanceof Codes.NewRecord) {
				exprs.add(write((Codes.NewRecord) bytecode));
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
				exprs.add(write((Codes.UnaryOperator) bytecode));
			} else if (bytecode instanceof Codes.Update) {
				exprs.add(write((Codes.Update) bytecode));
			} else if (bytecode instanceof Codes.Void) {
			}
			prev = bytecode;
			//Map<String, List<Expr>> labelMap = mapLabels(c.bytecodes());
			//write(bytecode, caseCount).forEach(exprs::add);

			if(bytecode instanceof Code.Compound) {
				output.println("\t" + bytecode.getClass().getName() + " {");
				write((CodeBlock) bytecode);
				output.println("}");
			}
			 else {
				output.println("\t" + bytecode);
			}

		}
		return cases;
	}

	// Seems right
	private Expr write(Codes.FieldLoad c) {
		List<Expr> exprs = new ArrayList<>();

		int level = getFieldLevel(c.type(0), c.field);

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
														factory.createValue(8)
												),
												factory.createConst(
														factory.createExprType(getType(c.fieldType())),
														factory.createValue(level+1))
										)
								)
						)
				)
		);

		return factory.createBlock(null, exprs);
	}

	private Expr write(Codes.NewRecord b) {
		List<Expr> exprs = new ArrayList<>();

		exprs.add(createBaseAddressInit());
		exprs.add(createPointerAssignment(b.target(0), getMetaType(b.type(0))));
		exprs.add(createBaseAddressAssignment((b.operands().length+1)*8));
		exprs.add(createConstructLengthAssignment(b.operands().length, getRecordMetaType(b.type(0)), b.target(0)));

		for (int i = 0; i < b.operands().length; i++) {
			exprs.add(
					factory.createStore(
							factory.createExprType(Expr.INT),
							(i+1)*8,
							null,
							factory.createGetLocal(
									factory.createVar("$"+b.target(0))
							),
							factory.createGetLocal(
									factory.createVar("$"+b.operand(i)) // FIXME: 5/05/16 Could be problems here.
							)
					)
			);
			exprs.add( // TODO: 23/05/16 - Sort out how this is going to work.
					factory.createStore(
							factory.createExprType(TYPE_VAR_TYPE),
							((i+1)*8)+4,
							null,
							factory.createGetLocal(
									factory.createVar("$"+b.target(0))
							),
							factory.createGetLocal(
									factory.createVar("$"+TYPE_VAR_NAME+b.operand(i)) // FIXME: 5/05/16 Could be problems here.
							)
					)
			);
		}
		//TODO: Make the record.

		return factory.createBlock(null ,exprs);
	}

	private Expr write(Codes.UnaryOperator c) {
		if (c.opcode() == Code.OPCODE_neg) {
			return factory.createSetLocal(
					factory.createVar("$" + c.target(0)),
					factory.createBinOp(
							factory.createExprType(getType(c.type(0))),
							Expr.mul,
							factory.createConst(
									factory.createExprType(Expr.INT),
									factory.createValue(-1)
							),
							factory.createGetLocal(
									factory.createVar("$" + c.operand(0))
							)
					)
			);
		} else {
			throw new Error("No there unary operators handled."); //TODO: work out other options here.
		}
	}

	//TODO: Keep mostly the same, change how local varables are loaded if i choose to store references in the array.
	private Expr write(Codes.Update c) {
		System.out.println(c);
		if (isArray(c.type(0))) {
			return writeArrayUpdate(c);
		} else {
			return writeRecordUpdate(c);
		}

	}

	/**
	 * Writes the array version of update.
	 * @param c
	 * @return
     */
	private Expr writeArrayUpdate(Codes.Update c) {
		return factory.createStore(
				factory.createExprType(Expr.INT),
				null,
				null,
				factory.createBinOp(
						factory.createExprType(Expr.INT),
						Expr.add,
						factory.createGetLocal(
								factory.createVar("$" + c.target(0))
						),
						factory.createBinOp(
								factory.createExprType(Expr.INT),
								Expr.mul,
								factory.createConst(
										factory.createExprType(Expr.INT),
										factory.createValue(8)
								),
								factory.createBinOp(
										factory.createExprType(Expr.INT),
										Expr.add,
										factory.createConst(
												factory.createExprType(Expr.INT),
												factory.createValue(1)
										),
										factory.createGetLocal(
												factory.createVar("$" + c.key(0))
										)
								)
						)
				),
				factory.createGetLocal(
						factory.createVar("$" + c.operand(1))
				)
		);
	}

	private Expr writeRecordUpdate(Codes.Update c) {
		List<Expr> exprs = new ArrayList<>();

		int level = getFieldLevel((Type.EffectiveRecord) c.afterType, c.fields.get(0));

		exprs.add(factory.createStore(
				factory.createExprType(Expr.INT),
				null,
				null,
				factory.createBinOp(
						factory.createExprType(Expr.INT),
						Expr.add,
						factory.createGetLocal(
								factory.createVar("$" + c.target(0))
						),
						factory.createBinOp(
								factory.createExprType(Expr.INT),
								Expr.mul,
								factory.createConst(
										factory.createExprType(Expr.INT),
										factory.createValue(8)
								),
								factory.createConst(
										factory.createExprType(Expr.INT),
										factory.createValue(level+ 1)
								)
						)
				),
				factory.createGetLocal(
						factory.createVar("$" + c.operand(0))
				)
		)
		);

		return factory.createBlock(null, exprs); // TODO: Make a update for this.
	}


	//TODO: argument is going to be moved allong 4 in the allignment maby 8 to store current type of elements
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

	//TODO: Same problem with refrencing as update, need to change how this is done.
	private List<Expr> write(Codes.IndexOf c) {
		List<Expr> exprs = new ArrayList<>();

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
													factory.createValue(8)
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

	//TODO: Need to change depending on how i will now handle arrays, refrencing could make it more complcated then it already is.
	// Most likely keep the same and have a special case for arrays storing arrays.
	private List<Expr> write(Codes.NewArray bytecode) {
		List<Expr> exprs = new ArrayList<>();

		//Initializes the base address in memory.
		exprs.add(createBaseAddressInit());

		//TODO: make memory grow if the array make the size to big.

		exprs.add( //Sets the local var to the pointer to array location.
				createPointerAssignment(bytecode.target(0), getMetaType(bytecode.type(0)))
		);

		exprs.add( //Sets the local var to the pointer to array location.
				createBaseAddressAssignment((bytecode.operands().length+1)*8)
		);

		//Loading in the length
		exprs.add(createConstructLengthAssignment(
				bytecode.operands().length,
				getMetaType(bytecode.type(0).element()),
				bytecode.target(0)
		));

		for (int i = 0; i < bytecode.operands().length; i++) {
			exprs.add(
					factory.createStore(
							factory.createExprType(Expr.INT),
							(i+1)*8,
							null,
							factory.createGetLocal(
									factory.createVar("$"+bytecode.target(0))
							),
							factory.createGetLocal(
									factory.createVar("$"+bytecode.operand(i)) // FIXME: 5/05/16 Could be problems here.
							)
					)
			);
			exprs.add( // TODO: 23/05/16 - Sort out how this is going to work.
					factory.createStore(
							factory.createExprType(WasmFileWriter.TYPE_VAR_TYPE),
							((i+1)*8)+4,
							null,
							factory.createGetLocal(
									factory.createVar("$"+bytecode.target(0))
							),
							factory.createGetLocal(
									factory.createVar("$"+TYPE_VAR_NAME+bytecode.operand(i)) // FIXME: 5/05/16 Could be problems here.
							)
					)
			);
		}

		return exprs;
	}


	//TODO: Will need to assign the stored values, rather than using local vars.
	private Expr write(Codes.Assign c) {
		List<Expr> exprs = new ArrayList<>();
		if (isArray(c.type(0))) {
//			List<Expr> exprs = new ArrayList<>();
			List<Expr> ops = new ArrayList<>();

			ops.add(
					factory.createGetLocal(
							factory.createVar("$"+c.operand(0))
					)
			);

			exprs.add(factory.createSetLocal(
					factory.createVar(
							"$" + c.target(0)),
					factory.createCall(
							factory.createVar("$DeepMemoryCopy"),
							ops
					)
			));

		} else if (isRecord(c.type(0))) {
			List<Expr> ops = new ArrayList<>();

			ops.add(
					factory.createGetLocal(
							factory.createVar("$"+c.operand(0))
					)
			);

			exprs.add(factory.createSetLocal(
					factory.createVar(
							"$" + c.target(0)),
					factory.createCall(
							factory.createVar("$DeepMemoryCopy"),
							ops
					)
			));

		} else {

			exprs.add(factory.createSetLocal(
					factory.createVar(
							"$" + c.target(0)),
					factory.createGetLocal(
							factory.createVar("$" + c.operand(0)
							)
					)
			));

		}
		exprs.add(factory.createSetLocal(
				factory.createVar(
						"$"+TYPE_VAR_NAME+c.target(0)
				),
				factory.createGetLocal(
						factory.createVar("$"+TYPE_VAR_NAME+c.operand(0))
				)
		));
		return factory.createBlock(null, exprs);
	}

	//TODO: Same as new array problem.
	private Expr writeAssignArray(Codes.Assign c) {
		String label = getLabel();
		String var = getVar();

		List<Expr> then = new ArrayList<>();

		Expr.Store store = factory.createStore( //Creates the load and store from old to new.
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
										factory.createValue(8)
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
												factory.createValue(8)
										)
								)
						)

				)
		);

		then.add(store);

		Expr.SetLocal increament = factory.createSetLocal( // Increments the varable.
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

		Expr.If loopContinue = factory.createIf( //Checks if it will loop again and then loops.
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

		Expr setLocal = createPointerAssignment(c.target(0), getMetaType(c.type(0))); //Assigns the pointer.

		Expr.Store storeLength = factory.createStore( //Store the length.
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

		then.add(setLocal); //Sets the local.
		then.add(creatingVar); //Create Var
		then.add(storeLength); // Store length
		then.add(loop); //Loop and add.

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
														factory.createValue(8)
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

	//TODO: If varables are being compared they will need to be loaded from the store.
	private Expr write(Codes.If c) {
		if (isArray(c.type(0))){
			return writeArrayIf(c);
		} else if (isRecord(c.type(0))) {
			return writeRecordIf(c);
		}else {
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

	//TODO: Follow the new array loading conventions.
	//TODO: Fix this check... it is complicated.
	private Expr writeArrayIf(Codes.If c) {
		String var = getVar();
		String label = getLabel();

		List<Expr> cases = new ArrayList<>();

		List<Expr> then = new ArrayList<>();

		then.add( //Sets the PC value.
				factory.createSetLocal(
						factory.createVar(PC),
						factory.createConst(
								factory.createExprType(Expr.INT),
								factory.createValue(labelMap.get(c.target))
						)
				)
		);
		then.add(factory.createBr( //Creates a branch to the switch statement.
				factory.createVar(BLOCK_NAME),
				null));

		Expr.If secondIf = factory.createIf( // Checks the length is correct.
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

		then.add(secondIf); // Adds the length check.

		Expr.SetLocal increment = factory.createSetLocal( // Increments the i Varable.
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

		then.add(increment);

		List<Expr> cont = new ArrayList<>();

		cont.add(factory.createBr(factory.createVar("$"+label), null));

		Expr.If loopContinue = factory.createIf( // Checks if the loop will continue.
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

		Expr.If firstIf = factory.createIf( // Checks the to veriables match.
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
														factory.createValue(8)
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
														factory.createValue(8)
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

	// Can be done by above function.
	private Expr writeRecordIf(Codes.If c) {
//		List<Expr> exprs = new ArrayList<>();


		String var = getVar();
		String label = getLabel();

		List<Expr> cases = new ArrayList<>();

		List<Expr> then = new ArrayList<>();

		then.add( //Sets the PC value.
				factory.createSetLocal(
						factory.createVar(PC),
						factory.createConst(
								factory.createExprType(Expr.INT),
								factory.createValue(labelMap.get(c.target))
						)
				)
		);
		then.add(factory.createBr( //Creates a branch to the switch statement.
				factory.createVar(BLOCK_NAME),
				null));

		Expr.If secondIf = factory.createIf( // Checks the length is correct.
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

		then.add(secondIf); // Adds the length check.

		Expr.SetLocal increment = factory.createSetLocal( // Increments the i Varable.
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

		then.add(increment);

		List<Expr> cont = new ArrayList<>();

		cont.add(factory.createBr(factory.createVar("$"+label), null));

		Expr.If loopContinue = factory.createIf( // Checks if the loop will continue.
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

		Expr.If firstIf = factory.createIf( // Checks the to veriables match.
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
														factory.createValue(8)
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
														factory.createValue(8)
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

//		return factory.createBlock(null, exprs);
	}

	/**
	 * Sets the PC value to match the Branch label int. Then branches to the start.
	 *
	 * @param c - Goto statement.
	 * @return
     */
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

	/**
	 * Creates a If statement. For handling the switch statements related to branching.
	 * If the PC value equals the checked value then all the expressions will be handled.
	 *
	 * @param exprs - List of exprs to be processed.
	 * @param c - Value to be checked against.
     * @return
     */
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

	//TODO: Same as above funtion.
	private Expr writeConstantArray(Codes.Const c) {
		List<Expr> exprs = new ArrayList<>();

		exprs.add(
				createBaseAddressInit()
		);

		//TODO: make memory grow if the array make the size to big.

		exprs.add( //Sets the local var to the pointer to array location.
				createPointerAssignment(c.target(0), getMetaType(c.constant.type()))
		);

		Constant.Array array = (Constant.Array) c.constant;

		exprs.add( //Sets the local var to the pointer to array location.
				createBaseAddressAssignment((array.values.size()+1)*8)
		);

		exprs.add(createConstructLengthAssignment(array.values.size(),
				getMetaType(((Type.Array) c.constant.type()).element()),
				c.target(0)
		));


		for (int i = 0; i < array.values.size(); i++) {
			exprs.add(
					factory.createStore(
							factory.createExprType(Expr.INT),
							(i+1)*8, //FIXME: Use a lambda or a function here.
							null,
							factory.createGetLocal(
									factory.createVar("$"+c.target(0))
							),
							factory.createConst(
									factory.createExprType(Expr.INT),
									factory.createValue(new Integer(array.values.get(i).toString())) //TODO: Fix this up.
							)
					)
			);
		}


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

	//TODO: Fix if refrencing changes.
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

	//TODO: May need to load varables from heap
	private Expr write(Codes.Return c) {
		if (c.operands().length == 0){
			return null;//TODO: add something in here for if the based on return values needed.
		} else {
			return factory.createReturn(factory.createGetLocal(
					factory.createVar("$"+c.operand(0))));
		}
	}

	//TODO: May need to load varables from the heap.
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
	//TODO: Another plan, add a type variable in for each var.
	private List<FunctionElement.Local> writeVariable(CodeBlock d, List<Integer> variableList) {
		List<FunctionElement.Local> locals = new ArrayList<>();
		labelMap.put(BASE_LABEL, labelNum++);
		int oldWasmLabelNumber = wasmLabelNumber;
		List<Code> codes = new ArrayList<>(d.bytecodes());
		for(int i = 0; i < codes.size(); i++) {
			Code bytecode = codes.get(i);
			if (bytecode instanceof Codes.ArrayGenerator) {
			} else if (bytecode instanceof Codes.Assert) {
				codes.remove(bytecode);
				Codes.Assert a = (Codes.Assert) bytecode;
				int temp = i;
				flatten(temp, codes, a.bytecodes());
				i--;
			} else if (bytecode instanceof Codes.Assign) {
				writeVariable((Codes.Assign) bytecode, variableList).forEach(locals::add);
			} else if (bytecode instanceof Codes.Assume) {
				codes.remove(bytecode);
				Codes.Assume a = (Codes.Assume) bytecode;
				int temp = i;
				flatten(temp, codes, a.bytecodes());
				i--;
			} else if (bytecode instanceof Codes.BinaryOperator) {
				writeVariable((Codes.BinaryOperator) bytecode, variableList).forEach(locals::add);
			} else if (bytecode instanceof Codes.Const) {
				writeVariable((Codes.Const) bytecode, variableList).forEach(locals::add);
			} else if (bytecode instanceof Codes.Convert) {
			} else if (bytecode instanceof Codes.Debug) {
			} else if (bytecode instanceof Codes.Dereference) {
			} else if (bytecode instanceof Codes.Fail) {
			} else if (bytecode instanceof Codes.FieldLoad) {
				writeVariable((Codes.FieldLoad) bytecode, variableList).forEach(locals::add);
			} else if (bytecode instanceof Codes.Goto) {
			} else if (bytecode instanceof Codes.If) {
				writeVariable((Codes.If) bytecode, variableList).forEach(locals::add);
			} else if (bytecode instanceof Codes.IfIs) {
			} else if (bytecode instanceof Codes.IndexOf) {
				writeVariable((Codes.IndexOf) bytecode, variableList).forEach(locals::add);
			} else if (bytecode instanceof Codes.IndirectInvoke) {
			} else if (bytecode instanceof Codes.Invariant) {
			} else if (bytecode instanceof Codes.Invert) {
			} else if (bytecode instanceof Codes.Invoke) {
				writeVariable((Codes.Invoke) bytecode, variableList).forEach(locals::add);
			} else if (bytecode instanceof Codes.Label) {
				if (!labelMap.containsKey(((Codes.Label) bytecode).label)) {
					labelMap.put(((Codes.Label) bytecode).label, labelNum++);
				}
			} else if (bytecode instanceof Codes.Lambda) {
			} else if (bytecode instanceof Codes.LengthOf) {
				writeVariable((Codes.LengthOf) bytecode, variableList).forEach(locals::add);
			} else if (bytecode instanceof Codes.Loop) {
//				List<FunctionElement.Local> local = writeVariable((Codes.Loop) bytecode, variableList);
//				local.forEach(locals::add)
				codes.remove(bytecode);
				Codes.Loop a = (Codes.Loop) bytecode;
				String label = getLabel();
				codes.add(i, Codes.Label(label));
				int temp = i+1;
				temp = flatten(temp, codes, a.bytecodes());
				i--;
				codes.add(temp, Codes.Goto(label));

			} else if (bytecode instanceof Codes.Move) {
			} else if (bytecode instanceof Codes.NewArray) {
				writeVariable((Codes.NewArray) bytecode, variableList).forEach(locals::add);
			} else if (bytecode instanceof Codes.NewObject) {
			} else if (bytecode instanceof Codes.NewRecord) {
				writeVariable((Codes.NewRecord) bytecode, variableList).forEach(locals::add);
			} else if (bytecode instanceof Codes.Nop) {
			} else if (bytecode instanceof Codes.Not) {
			} else if (bytecode instanceof Codes.Quantify) {
			} else if (bytecode instanceof Codes.Switch) {
			} else if (bytecode instanceof Codes.UnaryOperator) {
				writeVariable((Codes.UnaryOperator) bytecode, variableList).forEach(locals::add);
			} else if (bytecode instanceof Codes.Update) {
			}
		}
		wasmLabelNumber = oldWasmLabelNumber;
		return locals;
	}

	private List<FunctionElement.Local> writeVariable(Codes.FieldLoad bytecode, List<Integer> variableList) {
		List<FunctionElement.Local> locals = new ArrayList<>();
		if (!variableList.contains(bytecode.target(0))){
			variableList.add(bytecode.target(0));
			locals.add(factory.createLocal("$"+bytecode.target(0),
					factory.createExprType(getType(bytecode.fieldType()))));
			locals.add(factory.createLocal("$"+WasmFileWriter.TYPE_VAR_NAME+bytecode.target(0),
					factory.createExprType(WasmFileWriter.TYPE_VAR_TYPE)));
		}
		return locals;
	}

	private List<FunctionElement.Local> writeVariable(Codes.NewRecord bytecode, List<Integer> variableList) {
		List<FunctionElement.Local> locals = new ArrayList<>();
		if (!variableList.contains(bytecode.target(0))){
			variableList.add(bytecode.target(0));
			locals.add(factory.createLocal("$"+bytecode.target(0),
					factory.createExprType(getType(bytecode.type(0)))));
			locals.add(factory.createLocal("$"+WasmFileWriter.TYPE_VAR_NAME+bytecode.target(0),
					factory.createExprType(WasmFileWriter.TYPE_VAR_TYPE)));
		}
		return locals;
	}

	private List<FunctionElement.Local> writeVariable(Codes.UnaryOperator bytecode, List<Integer> variableList) {
		List<FunctionElement.Local> locals = new ArrayList<>();
		if (!variableList.contains(bytecode.target(0))){
			variableList.add(bytecode.target(0));
			locals.add(factory.createLocal("$"+bytecode.target(0),
					factory.createExprType(getType(bytecode.type(0)))));
			locals.add(factory.createLocal("$"+WasmFileWriter.TYPE_VAR_NAME+bytecode.target(0),
					factory.createExprType(WasmFileWriter.TYPE_VAR_TYPE)));
		}
		return locals;
	}

	private List<FunctionElement.Local> writeVariable(Codes.If bytecode, List<Integer> variableList) {
		List<FunctionElement.Local> locals = new ArrayList<>();
		if (isArray(bytecode.type(0))) {
			getLabel(); //TODO: create a better solution than this.
			locals.add(factory.createLocal(getVar(), factory.createExprType(Expr.INT)));
		} else if (isRecord(bytecode.type(0))) {
			getLabel(); //TODO: create a better solution than this.
			locals.add(factory.createLocal(getVar(), factory.createExprType(Expr.INT)));
		}
		return locals;
	}

	private void addToLocal(List<FunctionElement.Local> locals, FunctionElement.Local local){
		if (local != null) {
			locals.add(local);
		}
	}

	private List<FunctionElement.Local> writeVariable(Codes.IndexOf bytecode, List<Integer> variableList) {
		List<FunctionElement.Local> locals = new ArrayList<>();
		if (!variableList.contains(bytecode.target(0))){
			variableList.add(bytecode.target(0));
			locals.add(factory.createLocal("$"+bytecode.target(0),
					factory.createExprType(getType(bytecode.type(0).element()))));
			locals.add(factory.createLocal("$"+WasmFileWriter.TYPE_VAR_NAME+bytecode.target(0),
					factory.createExprType(WasmFileWriter.TYPE_VAR_TYPE)));
		}
		return locals;
	}

	private List<FunctionElement.Local> writeVariable(Codes.LengthOf bytecode, List<Integer> variableList) {
		List<FunctionElement.Local> locals = new ArrayList<>();
		if (!variableList.contains(bytecode.target(0))) {
			variableList.add(bytecode.target(0));
			locals.add(factory.createLocal("$" + bytecode.target(0),
					factory.createExprType(Expr.INT)));
			locals.add(factory.createLocal("$"+WasmFileWriter.TYPE_VAR_NAME+bytecode.target(0),
					factory.createExprType(WasmFileWriter.TYPE_VAR_TYPE)));
		}
		return locals;
	}

	private List<FunctionElement.Local> writeVariable(Codes.NewArray bytecode, List<Integer> variableList) {
		List<FunctionElement.Local> locals = new ArrayList<>();
		if (!variableList.contains(bytecode.target(0))) {
			variableList.add(bytecode.target(0));
			locals.add(factory.createLocal("$" + bytecode.target(0),
					factory.createExprType(Expr.INT)));
			locals.add(factory.createLocal("$"+WasmFileWriter.TYPE_VAR_NAME+bytecode.target(0),
					factory.createExprType(WasmFileWriter.TYPE_VAR_TYPE)));
		}
		return locals;
	}

	private List<FunctionElement.Local> writeVariable(Codes.Assign bytecode, List<Integer> variableList) {
		List<FunctionElement.Local> locals = new ArrayList<>();
		if (isArray(bytecode.type(0))) {
//			getLabel();
//			locals.add(factory.createLocal(getVar(), factory.createExprType(Expr.INT)));
		}
		if (!variableList.contains(bytecode.target(0))){
			variableList.add(bytecode.target(0));
			locals.add(factory.createLocal("$"+bytecode.target(0),
					factory.createExprType(getType(bytecode.type(0)))));
			locals.add(factory.createLocal("$"+WasmFileWriter.TYPE_VAR_NAME+bytecode.target(0),
					factory.createExprType(WasmFileWriter.TYPE_VAR_TYPE)));
		}
		return locals;
	}

	/**
	 * Writes a local variable of an invocation call and adds it to the list of made vars if not there.
	 * @param bytecode
	 * @param variableList
     */
	private List<FunctionElement.Local> writeVariable(Codes.Invoke bytecode, List<Integer> variableList) {
		List<FunctionElement.Local> locals = new ArrayList<>();
		if (bytecode.targets().length <= 0 || variableList.contains(bytecode.target(0))){
			return locals;
		}
		else {
			variableList.add(bytecode.target(0));
		}
		List<Type> targets = bytecode.type(0).returns();
				//returnMap.get(bytecode.name.name());

		Type targetType = targets.get(0); //FIXME: Might be using incorrect target.

		locals.add(factory.createLocal("$"+bytecode.target(0),
				factory.createExprType(getType(targetType))));
		locals.add(factory.createLocal("$"+WasmFileWriter.TYPE_VAR_NAME+bytecode.target(0),
				factory.createExprType(WasmFileWriter.TYPE_VAR_TYPE)));
		return locals;
	}

	/**
	 * Writes a local variable that is used in a constant call.
	 * @param bytecode
	 * @param variableList
     */
	private List<FunctionElement.Local> writeVariable(Codes.Const bytecode, List<Integer> variableList) {
		List<FunctionElement.Local> locals = new ArrayList<>();
		if (!variableList.contains(bytecode.target(0))){
			variableList.add(bytecode.target(0));
			locals.add(factory.createLocal("$"+bytecode.target(),
					factory.createExprType(getType(bytecode.constant.type()))));
			locals.add(factory.createLocal("$"+WasmFileWriter.TYPE_VAR_NAME+bytecode.target(0),
					factory.createExprType(WasmFileWriter.TYPE_VAR_TYPE)));
		}
		return locals;
	}

	/**
	 * Looks at a list of variable if its not there will make the list variable and add it to the list.
	 * @param bytecode
	 * @param variableList
     */
	private List<FunctionElement.Local> writeVariable(Codes.BinaryOperator bytecode, List<Integer> variableList){
		List<FunctionElement.Local> locals = new ArrayList<>();
		if (!variableList.contains(bytecode.target(0))){
			variableList.add(bytecode.target(0));
			locals.add(factory.createLocal("$"+bytecode.target(0),
					factory.createExprType(getType(bytecode.type(0)))));
			locals.add(factory.createLocal("$"+WasmFileWriter.TYPE_VAR_NAME+bytecode.target(0),
					factory.createExprType(WasmFileWriter.TYPE_VAR_TYPE)));
		}
		//TODO:Work with all the types - assumption first is target type possible get type method.
		return locals;
	}


	private Expr createMemoryCopy() { //TODO: Work out the parameters.
		List<Expr> exprs = new ArrayList<>();
		return factory.createBlock(null, exprs);
	}

	private Function createMemoryCopyHelperFunction() {
		List<FunctionElement.Param> params = new ArrayList<>();
		FunctionElement.Result result = factory.createResult(factory.createExprType(Expr.INT));
		List<FunctionElement.Local> locals = new ArrayList<>();
		List<Expr> mainBlock = new ArrayList<>();

		// Work out what parameters are need.
		params.add(
				factory.createParam("$location", factory.createExprType(Expr.INT))
		);

		// Work out the local varables needed.
		locals.add(
				factory.createLocal("$length", factory.createExprType(Expr.INT))
		);

		locals.add(
				factory.createLocal("$newBase", factory.createExprType(Expr.INT))
		);

		locals.add(
				factory.createLocal("$inc", factory.createExprType(Expr.INT))
		);

		// Create the main block of code.

		List<Expr> exprs = new ArrayList<>();

		// Create a memory allocation of Size

		exprs.add( //Loads the length for later use.
				factory.createSetLocal(
						factory.createVar("$length"),
						factory.createLoad(
								factory.createExprType(Expr.INT),
								null,
								null,
								null,
								factory.createGetLocal(
										factory.createVar("$location")
								)
						)
				)
		);

		exprs.add( //Stores the old location into a new storeage location.
				factory.createSetLocal(
						factory.createVar("$newBase"),
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

		exprs.add( //Sets base to the point at a avalible memory location..
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
														factory.createValue(8)
												),
												factory.createBinOp(
														factory.createExprType(Expr.INT),
														Expr.add,
														factory.createGetLocal(
																factory.createVar("$newBase")
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

		exprs.add( //Stores the length.
				factory.createStore(
						factory.createExprType(Expr.INT),
						null,
						null,
						factory.createGetLocal(
								factory.createVar("$newBase")
						),
						factory.createGetLocal(
								factory.createVar("$length")
						)
				)
		);

		exprs.add(
				factory.createStore(
						factory.createExprType(Expr.INT),
						(4),
						null,
						factory.createGetLocal(
								factory.createVar("$newBase")
						),
						factory.createLoad(
								factory.createExprType(Expr.INT),
								null,
								(4),
								null,
								factory.createGetLocal(
										factory.createVar("$location") //TODO: Organise parameter for this.
								)
						)
				)
		);

		// For each element check its type.

		exprs.add( factory.createSetLocal(
				factory.createVar("$inc"),
				factory.createConst(
						factory.createExprType(Expr.INT),
						factory.createValue(1)
				)
		));

		// If it is a pointer then recursively call this function.

		List<Expr> then = new ArrayList<>();
		List<Expr> andThen = new ArrayList<>();
		List<Expr> alt = new ArrayList<>();
		List<Expr> funParams = new ArrayList<>();
		List<Expr> loopContents = new ArrayList<>();


		andThen.add( // If its not equal to both then it will just copy the values.
				factory.createStore(
						factory.createExprType(Expr.INT),
						null,
						null,
						factory.createBinOp(
								factory.createExprType(Expr.INT),
								Expr.add,
								factory.createGetLocal(
										factory.createVar("$newBase")
								),
								factory.createBinOp(
										factory.createExprType(Expr.INT),
										Expr.mul,
										factory.createGetLocal(
												factory.createVar("$inc")
										),
										factory.createConst(
												factory.createExprType(Expr.INT),
												factory.createValue(8)
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
												factory.createVar("$location") //TODO: Parameter
										),
										factory.createBinOp(
												factory.createExprType(Expr.INT),
												Expr.mul,
												factory.createGetLocal(
														factory.createVar("$inc")
												),
												factory.createConst(
														factory.createExprType(Expr.INT),
														factory.createValue(8)
												)
										)
								)
						)
				)
		);


		funParams.add( // Loads the location of the array to be copied.
				factory.createLoad(
						factory.createExprType(Expr.INT),
						null,
						null,
						null,
						factory.createBinOp(
								factory.createExprType(Expr.INT),
								Expr.add,
								factory.createGetLocal(
										factory.createVar("$location") //TODO: Parameter
								),
								factory.createBinOp(
										factory.createExprType(Expr.INT),
										Expr.mul,
										factory.createGetLocal(
												factory.createVar("$inc")
										),
										factory.createConst(
												factory.createExprType(Expr.INT),
												factory.createValue(8)
										)
								)
						)
				)
		);

		alt.add( // Calls a function if equal to eather the array type or record type.
				factory.createStore(
						factory.createExprType(Expr.INT),
						null,
						null,
						factory.createBinOp(
								factory.createExprType(Expr.INT),
								Expr.add,
								factory.createGetLocal(
										factory.createVar("$newBase")
								),
								factory.createBinOp(
										factory.createExprType(Expr.INT),
										Expr.mul,
										factory.createGetLocal(
												factory.createVar("$inc")
										),
										factory.createConst(
												factory.createExprType(Expr.INT),
												factory.createValue(8)
										)
								)
						),
						factory.createCall(
								factory.createVar("$DeepMemoryCopy"),
								funParams
						)

				)
		);

		then.add( // Checks if its a record if its not then it will finally copy normally else it will recursive copy.
				factory.createIf(
						factory.createRelOp(
								factory.createExprType(Expr.INT),
								Expr.NE,
								factory.createLoad(
										factory.createExprType(Expr.INT),
										null,
										null,
										null,
										factory.createBinOp(
												factory.createExprType(Expr.INT),
												Expr.add,
												factory.createGetLocal(
														factory.createVar("$location") //TODO: Parameter
												),
												factory.createBinOp(
														factory.createExprType(Expr.INT),
														Expr.add,
														factory.createBinOp(
																factory.createExprType(Expr.INT),
																Expr.mul,
																factory.createGetLocal(
																		factory.createVar("$inc")
																),
																factory.createConst(
																		factory.createExprType(Expr.INT),
																		factory.createValue(8)
																)
														),
														factory.createConst(
																factory.createExprType(Expr.INT),
																factory.createValue(4)
														)
												)
										)
								),
								factory.createConst(
										factory.createExprType(Expr.INT),
										factory.createValue(typeMap.get("record"))
								)
						),
						null,
						andThen,
						null,
						alt
				)
		);

		loopContents.add( // Checks if the its not equal to a array. If is then recursive copy.
				factory.createIf(
						factory.createRelOp(
								factory.createExprType(Expr.INT),
								Expr.NE,
								factory.createLoad(
										factory.createExprType(Expr.INT),
										null,
										null,
										null,
										factory.createBinOp(
												factory.createExprType(Expr.INT),
												Expr.add,
												factory.createGetLocal(
														factory.createVar("$location") //TODO: Parameter
												),
												factory.createBinOp(
														factory.createExprType(Expr.INT),
														Expr.add,
														factory.createBinOp(
																factory.createExprType(Expr.INT),
																Expr.mul,
																factory.createGetLocal(
																		factory.createVar("$inc")
																),
																factory.createConst(
																		factory.createExprType(Expr.INT),
																		factory.createValue(8)
																)
														),
														factory.createConst(
																factory.createExprType(Expr.INT),
																factory.createValue(4)
														)
												)
										)
								),
								factory.createConst(
										factory.createExprType(Expr.INT),
										factory.createValue(typeMap.get("array"))
								)
						),
						null,
						then,
						null,
						alt
				)
		);

		// Store the returned pointer in the new location.

		List<Expr> cont = new ArrayList<>();

		cont.add(factory.createBr(factory.createVar("$loop"), null));

		Expr.If loopContinue = factory.createIf( //Checks if it will loop again and then loops.
				factory.createRelOp(
						factory.createExprType(Expr.INT),
						Expr.LE,
						factory.createGetLocal(
								factory.createVar("$inc")
						),
						factory.createGetLocal(
								factory.createVar("$length")
						)
				),
				null,
				cont,
				null,
				null
		);

		// Else copy the value.

		Expr.SetLocal increment = factory.createSetLocal( // Increments the i Varable.
				factory.createVar("$inc"),
				factory.createBinOp(
						factory.createExprType(Expr.INT),
						Expr.add,
						factory.createGetLocal(
								factory.createVar("$inc")
						),
						factory.createConst(
								factory.createExprType(Expr.INT),
								factory.createValue(1)
						)
				)
		);
		// Copy type values.

		loopContents.add( // Copys the type from one place to another.
				factory.createStore(
						factory.createExprType(Expr.INT),
						null,
						null,
						factory.createBinOp(
								factory.createExprType(Expr.INT),
								Expr.add,
								factory.createGetLocal(
										factory.createVar("$newBase")
								),
								factory.createBinOp(
										factory.createExprType(Expr.INT),
										Expr.add,
										factory.createBinOp(
												factory.createExprType(Expr.INT),
												Expr.mul,
												factory.createGetLocal(
														factory.createVar("$inc")
												),
												factory.createConst(
														factory.createExprType(Expr.INT),
														factory.createValue(8)
												)
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
												factory.createVar("$location") //TODO: Parameter
										),
										factory.createBinOp(
												factory.createExprType(Expr.INT),
												Expr.add,
												factory.createBinOp(
														factory.createExprType(Expr.INT),
														Expr.mul,
														factory.createGetLocal(
																factory.createVar("$inc")
														),
														factory.createConst(
																factory.createExprType(Expr.INT),
																factory.createValue(8)
														)
												),
												factory.createConst(
														factory.createExprType(Expr.INT),
														factory.createValue(4)
												)
										)
								)
						)
				)
		);

		loopContents.add(increment);
		loopContents.add(loopContinue);

		// return the pointer value.

		exprs.add(
				factory.createLoop(
						"$loop",
						null,
						loopContents
				)
		);

		exprs.add(
				factory.createReturn(
						factory.createGetLocal(
								factory.createVar("$newBase")
						)
				)
		);

		mainBlock.add(factory.createBlock(null, exprs));

		return factory.createFunction("$DeepMemoryCopy", null, params, result, locals, mainBlock);
	}

	/**
	 * Used for assigning the length of a list.
	 *
	 * @param length
	 * @return
     */
	private Expr createConstructLengthAssignment(int length, int type, int target) {
		List<Expr> exprs = new ArrayList<>();
		exprs.add( factory.createStore(
				factory.createExprType(Expr.INT),
				null,
				null,
				factory.createGetLocal(
						factory.createVar("$"+target)
				),
				factory.createConst(
						factory.createExprType(Expr.INT),
						factory.createValue(length)
				)
		));
		exprs.add( factory.createStore(
				factory.createExprType(TYPE_VAR_TYPE),
				(4),
				null,
				factory.createGetLocal(
						factory.createVar("$"+target)
				),
				factory.createConst(
						factory.createExprType(TYPE_VAR_TYPE),
						factory.createValue(type)
				)
		));
		return factory.createBlock(null, exprs);
	}

	/**
	 * Creates a Expr that assigns a pointer to the target verable with appreate type.
	 * @param target
	 * @param type
     * @return
     */
	private Expr createPointerAssignment(int target, int type) {
		List<Expr> exprs = new ArrayList<>();
		exprs.add(factory.createSetLocal(
				factory.createVar("$"+target),
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
		));

		exprs.add(
				factory.createSetLocal(
						factory.createVar("$"+TYPE_VAR_NAME+target),
						factory.createConst(
								factory.createExprType(TYPE_VAR_TYPE),
								factory.createValue(type) //TODO: Sort out records here,
						)
				)
		);

		return factory.createBlock(null, exprs);
	}

	/**
	 * Assigns a new pointer to the base pointer.
	 * @param distanceFromOldToNew
	 * @return
     */
	private Expr createBaseAddressAssignment (int distanceFromOldToNew) {
		return factory.createStore(
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
						factory.createConst(factory.createExprType(Expr.INT), factory.createValue(distanceFromOldToNew)
				)
			)
		);

	}

	/**
	 * Code for Initializing the base addressing zone needs to be used
	 * when maing a new record or array.
	 * @return
     */
	private Expr createBaseAddressInit () {
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

		return factory.createIf(
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
				);
	}

	private int getFieldLevel(Type.EffectiveRecord type, String field) {
		List<String> fields = new ArrayList<>(type.fields().keySet());

		fields.sort(String::compareTo);

		return fields.indexOf(field);
	}


	private Integer getMetaType(Type t) {
		if (isArray(t)) {
			return typeMap.get("array");
		} else if (isRecord(t)){
			return typeMap.get("record"); //TODO: Make a type name that cant be copyed.
		} else { //TODO: Add record info here.
			if (typeMap.containsKey(t.toString())) {
				return typeMap.get(t.toString());
			}
			else {
				typeMap.put(t.toString(), typeNum++);
				return typeMap.get(t.toString());
			}
		}
	}

	private Integer getRecordMetaType(Type t) {
		if (typeMap.containsKey(t.toString())) {
			return typeMap.get(t.toString());
		}
		else {
			typeMap.put(t.toString(), typeNum++);
			return typeMap.get(t.toString());
		}

	}

	private boolean isArray(Type type) {
		if (type instanceof Type.Array) {
			return true;
		}
		return false;
	}

	private boolean isRecord(Type type) {
		if (type instanceof Type.Record) {
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
			case Code.OPCODE_neg:
				return Expr.NEG;
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

	public int flatten(int temp, List<Code> toAddTo, List<Code> toTakeFrom) {
		for (int ii = 0; temp < toAddTo.size() && ii < toTakeFrom.size(); temp++, ii++) {
			toAddTo.add(temp, toTakeFrom.get(ii));
		}
		return temp;
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
