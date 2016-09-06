package wywasm;

import java.io.*;
import java.util.*;

import ast.*;
import util.WastFactory;
import util.WyILWasmFactory;
import wycc.util.Pair;
import wyil.io.WyilFileReader;
import wyil.lang.*;

public class WasmFileWriter {

    public static final String TYPE_VAR_NAME = "VAR_TYPE_FOR_";
    public static final String TYPE_VAR_TYPE = "i32";

    //Bool values.
    public static final int TRUE = 1;
    public static final int FALSE = 0;
    //TYPES
    public static final String INT = "i32";
    public static final String BOOL = "i32";
    //OPCODES
    public static final String ADD = "add";
    public static final String SUB = "sub";
    public static final String DIV = "div_s";//TODO: Change when code changes.
    public static final String MUL = "mul";
    public static final String BITWISE_OR = "or";
    public static final String BITWISE_XOR = "xor";
    public static final String BITWISE_AND = "and";
    public static final String REM = "rem_s";//TODO: Change when code changes.
    //OPCODE EXTENSIONS
    public static final String SIGNED = "_s";
    public static final String UNSIGNED = "_u";
    public static final String NO_EXTENTION = "";
    public static final String PC = "$pc";
    public static final String BLOCK_NAME = "$START";
    public static final String BASE_LABEL = "$BASE";
    public static final Integer BASE_MEMORY_LOCATION = 0;
    public static final Integer BASE_MEMORY_VALUE = 4;
    public static final Integer BASE_MEMORY_INCORRECT_VALUE = 0;
    public static final String DEFAULT_LABEL_NAME = "WASMLABEL";
    public static final String DEFAULT_VAR_NAME = "$WASMVAR";
    public static final int START_MEMORY = 4096;
    //Functions TODO: remove if un-needed
    private Map<String, List<Type>> paramMap = new HashMap<>();
    private Map<String, List<Type>> returnMap = new HashMap<>();
    private PrintStream output;
    private WastFactory f;
    private WyILWasmFactory wf;
    private Map<String, Integer> typeMap;
    private Map<String, Integer> labelMap = new HashMap<>();
    private WyilFile.FunctionOrMethod currentMethod;
    private int labelNum = 0;
    private int typeNum = 0;

    //Related to the Default names.
    private int wasmLabelNumber = 0;
    private int wasmVarNumber = 0;

    public WasmFileWriter(PrintStream output, WastFactory f) {
        this.output = output;
        this.f = f;
        wf = new WyILWasmFactory(f);
        this.typeMap = new HashMap<>();
    }

    public static void main(String[] args) {
        try {
            // First, read the input file to generate WyilFile instance
            WyilFile file = new WyilFileReader(args[0]).read();
            // Second, pass WyilFile into wasm file writer
            new WasmFileWriter(System.out, new WastFactory.SWastFactory()).write(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void write(WyilFile file) throws IOException {
        initializeTypeMap();
        List<ModuleElement.Export> exports = new ArrayList<>();
        List<Function> functions = new ArrayList<>();

        //for (WyilFile.Type t : file.types()) {
        //    System.out.println(t);
//        }

        for (WyilFile.FunctionOrMethod d : file.functionOrMethods()) {
            currentMethod = d;
            functions.add(write(d));
            exports.add(f.createExport("\"" + d.name() + "\"", f.createVar("$" + d.name())));
            paramMap.put(d.name(), d.type().params());
            returnMap.put(d.name(), d.type().returns());

        }
        functions.add(createMemoryCopyHelperFunction());

        ModuleElement.Memory memory = f.createMemory(START_MEMORY, null, null);

        Module module = f.createModule(null, functions, null, exports, null, memory, null);

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
     */
    private Function write(WyilFile.FunctionOrMethod d) throws IOException {
        labelNum = 0; //FIXME: Make this case not needed some how.
        labelMap = new HashMap<>(); //TODO: Remove this it may be not needed.
        //indent(indent);

        //Map<CodeBlock.Index, Boolean> variableMap = loadVerables(d.body().indices());
        List<Integer> variableList = new ArrayList<>();

        List<FunctionElement.Param> params = null;
        List<FunctionElement.Local> paramLocals = new ArrayList<>();
        if (!d.type().params().isEmpty()) {
            params = writeParams(d.type().params(), variableList, paramLocals);
        }

        FunctionElement.Result result = null;
        if (!d.type().returns().isEmpty()) {
            result = writeReturns(d.type().returns(), variableList);
        }

        List<FunctionElement.Local> locals = new ArrayList<>();
        if (d.body() != null) {
            locals = writeVariable(d.body(), variableList);
        }

        paramLocals.forEach(locals::add);// Used for parameters

        locals.add(f.createLocal(PC, f.createExprType(Expr.INT)));
        List<Expr> mainBlock = new ArrayList<>();
        mainBlock.add(
                f.createSetLocal(
                        f.createVar(PC),
                        f.createConst(
                                f.createExprType(Expr.INT),
                                f.createValue(0)
                        )
                )
        );

        output.print(d.name());
        output.print(" ");
        output.println(d.type());
        List<Expr> exprs = null;
        if (d.body() != null) {
            reset();//TODO: Find a better way to go about this.
            exprs = write(d.body());
            reset();
        }

        //TODO think of a better way to do this.
        //FIXME: It might be better to return -1.
        if (exprs != null && !currentMethod.type().returns().isEmpty()) {
            exprs.add(f.createUnreachable());
        }
        //indent(indent);
        output.println();


        mainBlock.add(f.createLoop(null, BLOCK_NAME, exprs));


        return f.createFunction("$" + d.name(), null, params, result, locals, mainBlock);
    }

    private List<FunctionElement.Param> writeParams(List<Type> params, List<Integer> variableList, List<FunctionElement.Local> locals) {
        int i = 0;
        List<FunctionElement.Param> pars = new ArrayList<>();
        for (Type param : params) {
            variableList.add(i);
            locals.add(f.createLocal(
                    "$" + TYPE_VAR_NAME + i,
                    f.createExprType(TYPE_VAR_TYPE)
            ));
            pars.add(f.createParam("$" + i++, f.createExprType(getType(param))));
        }
        return pars;
    }

    private FunctionElement.Result writeReturns(List<Type> returns, List<Integer> variableList) {
        if (!returns.isEmpty()) {
            return f.createResult(f.createExprType(getType(returns.get(0))));
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
        for (int i = 0; i < codes.size(); i++) {
            Code bytecode = codes.get(i);
            if (bytecode instanceof Codes.ArrayGenerator) {
                exprs.add(write((Codes.ArrayGenerator) bytecode));
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
                exprs.add(f.createUnreachable());
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
                if (!(prev instanceof Codes.Goto) && //Checks statements that stop fall through.
                        !(prev instanceof Codes.Fail &&
                                !(prev instanceof Codes.Return))) { //TODO: Make appropriate method for this.
                    exprs.add(
                            f.createSetLocal(
                                    f.createVar(PC),
                                    f.createBinOp(
                                            f.createExprType(Expr.INT),
                                            Expr.add,
                                            f.createGetLocal(f.createVar(PC)),
                                            f.createConst(
                                                    f.createExprType(Expr.INT),
                                                    f.createValue(1)
                                            )
                                    )
                            )
                    );
                    exprs.add(
                            f.createBr(
                                    f.createVar(BLOCK_NAME),
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
                if (bytecode.opcode() == Codes.Quantify.OPCODE_quantify) {
                    continue;
                }
//                System.out.println("Loop opcode: "+bytecode.opcode());
//                for (Object j: ((Codes.Loop) bytecode).modifiedOperands) {System.out.println(j);}
                //write((Codes.Assert) bytecode).forEach(exprs::add);
                codes.remove(bytecode);
//                System.out.println(((CodeBlock) bytecode));
                Codes.Loop a = (Codes.Loop) bytecode;
                String label = getLabel();
                codes.add(i, Codes.Label(label));
                int temp = i + 1;
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
                exprs.add(write((Codes.Switch) bytecode));
            } else if (bytecode instanceof Codes.UnaryOperator) {
                exprs.add(write((Codes.UnaryOperator) bytecode));
            } else if (bytecode instanceof Codes.Update) {
                exprs.add(write((Codes.Update) bytecode));
            } else if (bytecode instanceof Codes.Void) {
            }
            prev = bytecode;
            //Map<String, List<Expr>> labelMap = mapLabels(c.bytecodes());
            //write(bytecode, caseCount).forEach(exprs::add);

            if (bytecode instanceof Code.Compound) {
                output.println("\t" + bytecode.getClass().getName() + " {");
                write((CodeBlock) bytecode);
                output.println("}");
            } else {
                output.println("\t" + bytecode);
            }

        }
        return cases;
    }

    private Expr write(Codes.Switch c) {
        List<Expr> exprs = new ArrayList<>();

        for (Pair<Constant, String> b : c.branches) {
            exprs.add(createSwitchBranch(b, c.operand(0)));
        }

        exprs.add(createSwitchBranch(c.defaultTarget));

        return f.createBlock(null, exprs);
    }

    private Expr createSwitchBranch(Pair<Constant, String> branch, int operand) {
        List<Expr> then = new ArrayList<>();

        then.add(createSwitchBranch(branch.second()));

        return f.createIf(
                f.createRelOp(
                        f.createExprType(Expr.INT),
                        Expr.EQ,
                        f.createGetLocal(
                                f.createVar("$" + operand)
                        ),
                        f.createConst(
                                writeConstantType((branch.first()).type()),
                                writeConstantValue(branch.first())
                        )
                ),
                null,
                then,
                null,
                null
        );
    }

    private Expr createSwitchBranch(String label) {
        List<Expr> exprs = new ArrayList<>();

        exprs.add(
                f.createSetLocal(
                        f.createVar(PC),
                        f.createConst(
                                f.createExprType(Expr.INT),
                                f.createValue(labelMap.get(label))
                        )
                )
        );

        exprs.add(
                f.createBr(
                        f.createVar(BLOCK_NAME),
                        null
                )
        );

        return f.createBlock(null, exprs);
    }

    private Expr write(Codes.ArrayGenerator c) {
        List<Expr> exprs = new ArrayList<>();

        //Follow normal array creation procedure
        exprs.add(wf.createBaseAddressInit());
        System.out.println("Array Type :" + c.type(0));
        exprs.add(wf.createPointerAssignment(c.target(0), getMetaType(c.type(0))));
        //May need modification as the old ones know there length
        exprs.add(wf.createBaseAddressAssignment(
                f.createBinOp(
                        f.createExprType(Expr.INT),
                        Expr.mul,
                        f.createBinOp(
                                f.createExprType(Expr.INT),
                                Expr.add,
                                f.createGetLocal(
                                        f.createVar(
                                                "$" + c.operand(1)
                                        )
                                ),
                                f.createConst(
                                        f.createExprType(Expr.INT),
                                        f.createValue(1)
                                )
                        ),
                        f.createConst(
                                f.createExprType(Expr.INT),
                                f.createValue(8)
                        )
                )
        ));

        exprs.add(wf.createConstructLengthAssignment(
                f.createGetLocal(
                        f.createVar(
                                "$" + c.operand(1)
                        )
                ),
                f.createConst(
                        f.createExprType(Expr.INT),
                        f.createValue(
                                getMetaType(c.type(0).element())
                        )
                ),
                f.createGetLocal(
                        f.createVar(
                                "$" + c.target(0)
                        )
                )
        ));

        //And other information at run time.
        //Where the length is set in the array generator.
        //Must be able to make a list of length 0

        //An whats being assigned is set the array generator.
        //System.out.println(c.operand(1));
        //Run time adding of values to the array based on length.
        //Need to create a function that creates a loop that takes a
        //expr / block of exprs and runs then repeatedly then loops around
        //Also will need to take a condition statement.

        //Name of the increment value.
        String loopVar = getVar();

        wf.createLoop(
                f.createSetLocal( //Setup for the start of the loop.
                        f.createVar(loopVar),
                        f.createConst(
                                f.createExprType(Expr.INT),
                                f.createValue(1) //Avoids computation later,
                                // setting it to one now.
                        )
                ),
                f.createRelOp( //Conditional checks if the loop var is at the current lenght.
                        f.createExprType(Expr.INT),
                        Expr.LE, //TODO: Work out if there is problem here.
                        f.createGetLocal(
                                f.createVar(loopVar)
                        ),
                        f.createGetLocal(
                                f.createVar("$" + c.operand(1))
                        )
                ),
                wf.createMemoryValueAndTypeStore( //Body of the function.
                        f.createBinOp( //Location of the location to be stored in.
                                f.createExprType(Expr.INT),
                                Expr.add,
                                f.createGetLocal(
                                        f.createVar("$" + c.target(0))
                                ),
                                f.createBinOp(
                                        f.createExprType(Expr.INT),
                                        Expr.mul,
                                        f.createGetLocal(
                                                f.createVar(loopVar)
                                        ),
                                        f.createConst(
                                                f.createExprType(Expr.INT),
                                                f.createValue(8) //TODO: Make this a field.
                                        )
                                )
                        ),
                        0, //Offset - 0 becasue the location is only knowen at runtime.
                        f.createGetLocal( //Value to be stored.
                                f.createVar("$" + c.operand(0))
                        ),
                        f.createGetLocal( //Type of the value to be stored.
                                f.createVar("$" + TYPE_VAR_NAME + c.operand(0))
                        ),
                        typeMap //Requires the type map
                ),
                f.createSetLocal( //Increment of the value in memory.
                        f.createVar(loopVar),
                        f.createBinOp(
                                f.createExprType(Expr.INT),
                                Expr.add,
                                f.createGetLocal(
                                        f.createVar(loopVar)
                                ),
                                f.createConst(
                                        f.createExprType(Expr.INT),
                                        f.createValue(1)
                                )
                        )
                ),
                getLabel() //Requires a label.
        );

        return f.createBlock(null, exprs);
    }

    // Seems right
    private Expr write(Codes.FieldLoad c) {
        List<Expr> exprs = new ArrayList<>();

        int level = getFieldLevel(c.type(0), c.field);

        exprs.add(
                f.createSetLocal(
                        f.createVar("$" + c.target(0)),
                        wf.createLoad(
                                f.createBinOp(
                                        f.createExprType(Expr.INT), //TODO: Extend for more types.
                                        Expr.add,
                                        f.createGetLocal(f.createVar("$" + c.operand(0))),
                                        f.createBinOp(
                                                f.createExprType(Expr.INT), //TODO: Extend for more types.
                                                Expr.mul,
                                                f.createConst(
                                                        f.createExprType(Expr.INT),
                                                        f.createValue(8)
                                                ),
                                                f.createConst(
                                                        f.createExprType(getType(c.fieldType())),
                                                        f.createValue(level + 1))
                                        )
                                ),
                                0
                        )
                )
        );

        return f.createBlock(null, exprs);
    }

    private Expr write(Codes.NewRecord b) {
        List<Expr> exprs = new ArrayList<>();

        exprs.add(wf.createBaseAddressInit());
        exprs.add(wf.createPointerAssignment(b.target(0), getMetaType(b.type(0))));
        exprs.add(wf.createBaseAddressAssignment((b.operands().length + 1) * 8));
        exprs.add(wf.createConstructLengthAssignment(b.operands().length, getRecordMetaType(b.type(0)), b.target(0)));

        for (int i = 0; i < b.operands().length; i++) {
            exprs.add(
                    wf.createStore(
                            f.createGetLocal(
                                    f.createVar("$" + b.target(0))
                            ),
                            (i + 1) * 8,
                            wf.createCompareAndDeepCopyCall( //Checks and then calls deep memory copy if required.
                                    f.createGetLocal(
                                            f.createVar("$" + b.operand(i))
                                    ),
                                    f.createGetLocal(
                                            f.createVar("$" + TYPE_VAR_NAME + b.operand(i))
                                    ),
                                    typeMap //Requires the type map to get the types for Array and Record.
                            )
                    )
            );
            exprs.add(
                    f.createStore(
                            f.createExprType(TYPE_VAR_TYPE),
                            ((i + 1) * 8) + 4,
                            null,
                            f.createGetLocal(
                                    f.createVar("$" + b.target(0))
                            ),
                            f.createGetLocal(
                                    f.createVar("$" + TYPE_VAR_NAME + b.operand(i))
                            )
                    )
            );
        }

        return f.createBlock(null, exprs);
    }

    private Expr write(Codes.UnaryOperator c) {
        if (c.opcode() == Code.OPCODE_neg) {
            return f.createSetLocal(
                    f.createVar("$" + c.target(0)),
                    f.createBinOp(
                            f.createExprType(getType(c.type(0))),
                            Expr.mul,
                            f.createConst(
                                    f.createExprType(Expr.INT),
                                    f.createValue(-1)
                            ),
                            f.createGetLocal(
                                    f.createVar("$" + c.operand(0))
                            )
                    )
            );
        } else {
            throw new Error("No there unary operators handled."); //TODO: work out other options here.
        }
    }

    private Expr write(Codes.Update c) {
        Pair<Expr, Integer> locationAndOffset = writeBaseUpdate(c, c.iterator());
        int operand;
        operand = c.operands()[c.operands().length-1];
        return wf.createStore(
                locationAndOffset.first(),
                locationAndOffset.second(),
                f.createGetLocal(
                        f.createVar("$"+operand)
                )
        );

    }

    private Pair<Expr, Integer> writeBaseUpdate(Codes.Update c, Iterator<Codes.LVal> iterator) {
        Pair<Expr, Integer> locationAndOffset = new Pair<>(null, 0);
        System.out.println("Should be running this fucntion");
        while (iterator.hasNext()) {
            Codes.LVal v = iterator.next();
            if (v instanceof Codes.ArrayLVal) {
                Codes.ArrayLVal av = (Codes.ArrayLVal) v;
                locationAndOffset = writeArrayUpdate(c, av, locationAndOffset.first());
            } else {
                Codes.RecordLVal rv = (Codes.RecordLVal) v;
                locationAndOffset = writeRecordUpdate(c, rv, locationAndOffset.first());
            }
        }
        return locationAndOffset;
    }

    private Pair<Expr, Integer> writeArrayUpdate(Codes.Update c, Codes.ArrayLVal av, Expr location) {

        if (location == null) {
            location = f.createGetLocal(
                    f.createVar("$" + c.target(0))
            );
        } else  {
            location = wf.createLoad(
                    location,
                    0
            );
        }

        return new Pair<>(
                f.createBinOp(
                        f.createExprType(Expr.INT),
                        Expr.add,
                        location,
                        f.createBinOp(
                                f.createExprType(Expr.INT),
                                Expr.mul,
                                f.createConst(
                                        f.createExprType(Expr.INT),
                                        f.createValue(8)
                                ),
                                f.createBinOp(
                                        f.createExprType(Expr.INT),
                                        Expr.add,
                                        f.createConst(
                                                f.createExprType(Expr.INT),
                                                f.createValue(1)
                                        ),
                                        f.createGetLocal(
                                                f.createVar("$" + av.indexOperand)
                                        )
                                )
                        )
                ),
                0
        );
    }

    private Pair<Expr, Integer> writeRecordUpdate(Codes.Update c, Codes.RecordLVal rv, Expr location) {
        int level = getFieldLevel(rv.rawType(), rv.field);

        if (location == null) {
            location = f.createGetLocal(
                    f.createVar("$" + c.target(0))
            );
        } else  {
            location = wf.createLoad(
                    location,
                    0
            );
        }

        return new Pair<>(
                f.createBinOp(
                        f.createExprType(Expr.INT),
                        Expr.add,
                        location,
                        f.createBinOp(
                                f.createExprType(Expr.INT),
                                Expr.mul,
                                f.createConst(
                                        f.createExprType(Expr.INT),
                                        f.createValue(8)
                                ),
                                f.createConst(
                                        f.createExprType(Expr.INT),
                                        f.createValue(level + 1)
                                )
                        )
                ),
                0
        );
    }


    private Expr write(Codes.LengthOf bytecode) {
        return f.createSetLocal(
                f.createVar("$" + bytecode.target(0)),
                f.createLoad(
                        f.createExprType(Expr.INT),
                        null,
                        null,
                        null,
                        f.createGetLocal(
                                f.createVar("$" + bytecode.operand(0))
                        ) //TODO: Fix the implemtaion fo loops.
                )
        );
    }

    private List<Expr> write(Codes.IndexOf c) {
        List<Expr> exprs = new ArrayList<>();

        exprs.add(
                f.createSetLocal(
                        f.createVar("$" + c.target(0)),
                        wf.createLoad(
                                f.createBinOp(
                                        f.createExprType(Expr.INT), //TODO: Extend for more types.
                                        Expr.add,
                                        f.createGetLocal(f.createVar("$" + c.operand(0))),
                                        f.createBinOp(
                                                f.createExprType(Expr.INT), //TODO: Extend for more types.
                                                Expr.mul,
                                                f.createConst(
                                                        f.createExprType(Expr.INT),
                                                        f.createValue(8)
                                                ),
                                                f.createBinOp(
                                                        f.createExprType(getType(c.type(0).element())),
                                                        Expr.add,
                                                        f.createGetLocal(
                                                                f.createVar("$" + c.operand(1))
                                                        ),
                                                        f.createConst(
                                                                f.createExprType(getType(c.type(0).element())),
                                                                f.createValue(1))
                                                )
                                        )
                                ),
                                0
                        )
                )
        );

        return exprs;
    }

    private List<Expr> write(Codes.NewArray bytecode) {
        List<Expr> exprs = new ArrayList<>();

        //Initializes the base address in memory.
        exprs.add(wf.createBaseAddressInit());

        //TODO: make memory grow if the array make the size to big.

        exprs.add( //Sets the local var to the pointer to array location.
                wf.createPointerAssignment(bytecode.target(0), getMetaType(bytecode.type(0)))
        );

        exprs.add( //Sets the local var to the pointer to array location.
                wf.createBaseAddressAssignment((bytecode.operands().length + 1) * 8)
        );

        //Loading in the length
        exprs.add(wf.createConstructLengthAssignment(
                bytecode.operands().length,
                getMetaType(bytecode.type(0).element()),
                bytecode.target(0)
        ));

        for (int i = 0; i < bytecode.operands().length; i++) {
            exprs.add(
                    wf.createMemoryValueAndTypeStore(
                            f.createGetLocal(
                                    f.createVar("$" + bytecode.target(0))
                            ),
                            (i + 1) * 8,
                            f.createGetLocal(
                                    f.createVar("$" + bytecode.operand(i))
                            ),
                            f.createGetLocal(
                                    f.createVar("$" + TYPE_VAR_NAME + bytecode.operand(i))
                            ),
                            typeMap //Requires type map.
                    )
            );
        }

        return exprs;
    }

    private Expr write(Codes.Assign c) {
        List<Expr> exprs = new ArrayList<>();
        if (isArray(c.type(0)) || isRecord(c.type(0))) {
            List<Expr> ops = new ArrayList<>();

            ops.add(
                    f.createGetLocal(
                            f.createVar("$" + c.operand(0))
                    )
            );

            exprs.add(f.createSetLocal(
                    f.createVar(
                            "$" + c.target(0)),
                    f.createCall(
                            f.createVar("$DeepMemoryCopy"),
                            ops
                    )
            ));
        } else {
            exprs.add(f.createSetLocal(
                    f.createVar(
                            "$" + c.target(0)),
                    f.createGetLocal(
                            f.createVar("$" + c.operand(0)
                            )
                    )
            ));

        }
        exprs.add(f.createSetLocal(
                f.createVar(
                        "$" + TYPE_VAR_NAME + c.target(0)
                ),
                f.createGetLocal(
                        f.createVar("$" + TYPE_VAR_NAME + c.operand(0))
                )
        ));
        return f.createBlock(null, exprs);
    }

    //TODO: If variables are being compared they will need to be loaded from the store.
    private Expr write(Codes.If c) {
        if (isArray(c.type(0))) {
            return writeArrayIf(c);
        } else if (isRecord(c.type(0))) {
            return writeRecordIf(c);
        } else {
            List<Expr> then = new ArrayList<>();
            then.add(
                    f.createSetLocal(
                            f.createVar(PC),
                            f.createConst(
                                    f.createExprType(Expr.INT),
                                    f.createValue(labelMap.get(c.target))
                            )
                    )
            );
            then.add(f.createBr(
                    f.createVar(BLOCK_NAME),
                    null)); //TODO: Find a better way to fix up a branching statment.
            return f.createIf(f.createRelOp(
                    f.createExprType(Expr.INT),
                    getOp(c.opcode()),
                    f.createGetLocal(f.createVar("$" + c.operand(0))),
                    f.createGetLocal(f.createVar("$" + c.operand(1)))
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
                f.createSetLocal(
                        f.createVar(PC),
                        f.createConst(
                                f.createExprType(Expr.INT),
                                f.createValue(labelMap.get(c.target))
                        )
                )
        );
        then.add(f.createBr( //Creates a branch to the switch statement.
                f.createVar(BLOCK_NAME),
                null));

        Expr.If secondIf = wf.createIf( // Checks the length is not grater then the iteration variable.
                f.createRelOp(
                        f.createExprType(Expr.INT), //TODO: Fix this to work with more types.
                        Expr.EQ,
                        f.createGetLocal(
                                f.createVar(var)
                        ),
                        wf.createLoad(
                                f.createGetLocal(
                                        f.createVar("$" + c.operand(0))
                                ),
                                0
                        )
                ),
                f.createBlock(null,then),
                null
        );

        then = new ArrayList<>();

        then.add(secondIf); // Adds the length check.

        Expr.SetLocal increment = f.createSetLocal( // Increments the i Varable.
                f.createVar(var),
                f.createBinOp(
                        f.createExprType(Expr.INT),
                        Expr.add,
                        f.createGetLocal(
                                f.createVar(var)
                        ),
                        f.createConst(
                                f.createExprType(Expr.INT),
                                f.createValue(1)
                        )
                )
        );

        then.add(increment);

        List<Expr> cont = new ArrayList<>();

        cont.add(f.createBr(f.createVar("$" + label), null));

        Expr.If loopContinue = wf.createIf( // Checks if the loop will continue.
                f.createRelOp(
                        f.createExprType(Expr.INT),
                        Expr.LE,
                        f.createGetLocal(
                                f.createVar(var)
                        ),
                        wf.createLoad(
                                f.createGetLocal(
                                        f.createVar("$" + c.operand(0))
                                ),
                                0
                        )
                ),
                f.createBlock(null, cont),
                null
        );

        then.add(loopContinue);

        Expr.If firstIf = f.createIf( // Checks the to veriables match.
                f.createRelOp(
                        f.createExprType(Expr.INT),
                        getOp(c.opcode()),
                        wf.createLoad(
                                f.createBinOp(
                                        f.createExprType(Expr.INT),
                                        Expr.add,
                                        f.createGetLocal(
                                                f.createVar("$" + c.operand(0))
                                        ),
                                        f.createBinOp(
                                                f.createExprType(Expr.INT),
                                                Expr.mul,
                                                f.createGetLocal(
                                                        f.createVar(var)
                                                ),
                                                f.createConst(
                                                        f.createExprType(Expr.INT),
                                                        f.createValue(8)
                                                )
                                        )
                                ),
                                0
                        ),
                        wf.createLoad(
                                f.createBinOp(
                                        f.createExprType(Expr.INT),
                                        Expr.add,
                                        f.createGetLocal(
                                                f.createVar("$" + c.operand(1))
                                        ),
                                        f.createBinOp(
                                                f.createExprType(Expr.INT),
                                                Expr.mul,
                                                f.createGetLocal(
                                                        f.createVar(var)
                                                ),
                                                f.createConst(
                                                        f.createExprType(Expr.INT),
                                                        f.createValue(8)
                                                )
                                        )
                                ),
                                0
                        )
                ),
                null,
                then,
                null,
                null
        );

        then = new ArrayList<>();
        then.add(firstIf);


        Expr.SetLocal creatingVar = f.createSetLocal(
                f.createVar(var),
                f.createConst(
                        f.createExprType(Expr.INT),
                        f.createValue(1)
                )
        );

        Expr.Loop loop = f.createLoop(null, "$" + label, then);

        then = new ArrayList<>();

        ArrayList<Expr> temp = new ArrayList<>();
        temp.add( //Sets the PC value.
                f.createSetLocal(
                        f.createVar(PC),
                        f.createConst(
                                f.createExprType(Expr.INT),
                                f.createValue(labelMap.get(c.target))
                        )
                )
        );
        temp.add(f.createBr( //Creates a branch to the switch statement.
                f.createVar(BLOCK_NAME),
                null));

        then.add(//Checks special case where lenght is 0
                f.createIf(
                        f.createRelOp(
                                f.createExprType(Expr.INT),
                                Expr.EQ,
                                f.createBinOp(
                                        f.createExprType(Expr.INT),
                                        Expr.add,
                                        wf.createLoad(
                                                f.createGetLocal(
                                                        f.createVar("$" + c.operand(0))
                                                ),
                                                0
                                        ),
                                        wf.createLoad(
                                                f.createGetLocal(
                                                        f.createVar("$" + c.operand(0))
                                                ),
                                                0
                                        )
                                ),
                                f.createConst(
                                        f.createExprType(Expr.INT),
                                        f.createValue(0)
                                )
                        ),
                        null,
                        temp,
                        null,
                        null
                )
        );
        then.add(creatingVar);
        then.add(loop);

        cases.add(
                createArrayIfCase(getOp(c.opcode()), c.target, c.operand(0), c.operand(1), then)
        );

        return f.createBlock(null, cases);
    }

    private Expr createArrayIfCase(String operation, String target, int operandOne, int operandTwo, List<Expr> then) {
        List<Expr> elseArray = null;

        switch (operation) {
            case Expr.NE:
                elseArray = new ArrayList<>();
                then.add( //Sets the PC value.
                        f.createSetLocal(
                                f.createVar(PC),
                                f.createConst(
                                        f.createExprType(Expr.INT),
                                        f.createValue(labelMap.get(target))
                                )
                        )
                );
                then.add(f.createBr( //Creates a branch to the switch statement.
                        f.createVar(BLOCK_NAME),
                        null));
                break;
            default:
                elseArray = null;
        }

        return f.createIf(
                f.createRelOp(
                        f.createExprType(Expr.INT),
                        //operation,
                        Expr.EQ,
                        f.createLoad(
                                f.createExprType(Expr.INT),
                                null,
                                null,
                                null,
                                f.createGetLocal(
                                        f.createVar("$" + operandOne)
                                )
                        ),
                        f.createLoad(
                                f.createExprType(Expr.INT),
                                null,
                                null,
                                null,
                                f.createGetLocal(
                                        f.createVar("$" + operandTwo)
                                )
                        )
                ),
                null,
                then,
                null,
                elseArray
        );


    }

    // Can be done by above function.
    private Expr writeRecordIf(Codes.If c) {
//		List<Expr> exprs = new ArrayList<>();


        String var = getVar();
        String label = getLabel();

        List<Expr> cases = new ArrayList<>();

        List<Expr> then = new ArrayList<>();

        then.add( //Sets the PC value.
                f.createSetLocal(
                        f.createVar(PC),
                        f.createConst(
                                f.createExprType(Expr.INT),
                                f.createValue(labelMap.get(c.target))
                        )
                )
        );
        then.add(f.createBr( //Creates a branch to the switch statement.
                f.createVar(BLOCK_NAME),
                null));

        Expr.If secondIf = wf.createIf( // Checks the length is correct.
                f.createRelOp(
                        f.createExprType(Expr.INT), //TODO: Fix this to work with more types.
                        Expr.EQ,
                        f.createGetLocal(
                                f.createVar(var)
                        ),
                        wf.createLoad(
                                f.createGetLocal(
                                        f.createVar("$" + c.operand(0))
                                ),
                                0
                        )
                ),
                f.createBlock(null,then),
                null
        );

        then = new ArrayList<>();

        then.add(secondIf); // Adds the length check.

        Expr.SetLocal increment = f.createSetLocal( // Increments the i Varable.
                f.createVar(var),
                f.createBinOp(
                        f.createExprType(Expr.INT),
                        Expr.add,
                        f.createGetLocal(
                                f.createVar(var)
                        ),
                        f.createConst(
                                f.createExprType(Expr.INT),
                                f.createValue(1)
                        )
                )
        );

        then.add(increment);

        List<Expr> cont = new ArrayList<>();

        cont.add(f.createBr(f.createVar("$" + label), null));

        Expr.If loopContinue = wf.createIf( // Checks if the loop will continue.
                f.createRelOp(
                        f.createExprType(Expr.INT),
                        Expr.LE,
                        f.createGetLocal(
                                f.createVar(var)
                        ),
                        wf.createLoad(
                                f.createGetLocal(
                                        f.createVar("$" + c.operand(0))
                                ),
                                0
                        )
                ),
                f.createBlock(null, cont),
                null
        );

        then.add(loopContinue);

        Expr.If firstIf = f.createIf( // Checks the to veriables match.
                f.createRelOp(
                        f.createExprType(Expr.INT),
                        getOp(c.opcode()),
                        wf.createLoad(
                                f.createBinOp(
                                        f.createExprType(Expr.INT),
                                        Expr.add,
                                        f.createGetLocal(
                                                f.createVar("$" + c.operand(0))
                                        ),
                                        f.createBinOp(
                                                f.createExprType(Expr.INT),
                                                Expr.mul,
                                                f.createGetLocal(
                                                        f.createVar(var)
                                                ),
                                                f.createConst(
                                                        f.createExprType(Expr.INT),
                                                        f.createValue(8)
                                                )
                                        )
                                ),
                                0
                        ),
                        wf.createLoad(
                                f.createBinOp(
                                        f.createExprType(Expr.INT),
                                        Expr.add,
                                        f.createGetLocal(
                                                f.createVar("$" + c.operand(1))
                                        ),
                                        f.createBinOp(
                                                f.createExprType(Expr.INT),
                                                Expr.mul,
                                                f.createGetLocal(
                                                        f.createVar(var)
                                                ),
                                                f.createConst(
                                                        f.createExprType(Expr.INT),
                                                        f.createValue(8)
                                                )
                                        )
                                ),
                                0
                        )
                ),
                null,
                then,
                null,
                null
        );

        then = new ArrayList<>();
        then.add(firstIf);


        Expr.SetLocal creatingVar = f.createSetLocal(
                f.createVar(var),
                f.createConst(
                        f.createExprType(Expr.INT),
                        f.createValue(1)
                )
        );

        Expr.Loop loop = f.createLoop(null, "$" + label, then);

        then = new ArrayList<>();

        then.add(creatingVar);
        then.add(loop);


        cases.add(
                createArrayIfCase(getOp(c.opcode()), c.target, c.operand(0), c.operand(1), then)
        );

        return f.createBlock(null, cases);
    }

    /**
     * Sets the PC value to match the Branch label int. Then branches to the start.
     *
     * @param c - Goto statement.
     */
    private List<Expr> write(Codes.Goto c) {
        List<Expr> exprs = new ArrayList<>();

        exprs.add(
                f.createSetLocal(
                        f.createVar(PC),
                        f.createConst(
                                f.createExprType(Expr.INT),
                                f.createValue(labelMap.get(c.target))
                        )
                )
        ); // Set the PC to be the label to go to.

        exprs.add(f.createBr(f.createVar(BLOCK_NAME), null));
        //Branch to the start so the the label case is selected.

        return exprs;
    }

    /**
     * Creates a If statement. For handling the switch statements related to branching. If the PC
     * value equals the checked value then all the expressions will be handled.
     *
     * @param exprs - List of exprs to be processed.
     * @param c     - Value to be checked against.
     */
    private Expr.If createCase(List<Expr> exprs, int c) {
        return f.createIf(
                f.createRelOp(
                        f.createExprType(Expr.INT),
                        Expr.EQ,
                        f.createGetLocal(f.createVar(PC)),
                        f.createConst(f.createExprType(Expr.INT),
                                f.createValue(c)
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
        if (c.constant.type() instanceof Type.Array) {
            //if (c.constant.type().equals(Type.Array(Type.T_INT, true))) {
            return writeConstantArray(c);
        }
//		output.println(c.constant.type());
//		output.println(Type.T_ARRAY_ANY);
        return f.createSetLocal(f.createVar("$" + c.target()),
                f.createConst(writeConstantType(c.constant.type()), writeConstantValue(c.constant)));
    }

    //TODO: Same as above function.
    private Expr writeConstantArray(Codes.Const c) {
        List<Expr> exprs = new ArrayList<>();

        exprs.add(
                wf.createBaseAddressInit()
        );

        //TODO: make memory grow if the array make the size to big.

        exprs.add( //Sets the local var to the pointer to array location.
                wf.createPointerAssignment(c.target(0), getMetaType(c.constant.type()))
        );

        Constant.Array array = (Constant.Array) c.constant;

        exprs.add( //Sets the local var to the pointer to array location.
                wf.createBaseAddressAssignment((array.values.size() + 1) * 8)
        );

        exprs.add(wf.createConstructLengthAssignment(array.values.size(),
                getMetaType(((Type.Array) c.constant.type()).element()),
                c.target(0)
        ));


        for (int i = 0; i < array.values.size(); i++) {
            exprs.add(
                    f.createStore(
                            f.createExprType(Expr.INT),
                            (i + 1) * 8, //FIXME: Use a lambda or a function here.
                            null,
                            f.createGetLocal(
                                    f.createVar("$" + c.target(0))
                            ),
                            f.createConst(
                                    f.createExprType(Expr.INT),
                                    f.createValue(new Integer(array.values.get(i).toString())) //TODO: Fix this up.
                            )
                    )
            );
        }


        return f.createBlock(null, exprs);
    }

    private ExprElement.Type writeConstantType(Type type) {
        //System.out.println(type);
        if (type.equals(Type.T_INT)) {
            return f.createExprType(Expr.INT);
        } else if (type.equals(Type.T_BOOL)) {
            return f.createExprType(Expr.INT);
        } else if (type.equals(Type.T_ARRAY_ANY)) {
            //TODO: Add in the create a constant array;
//			System.out.println("Should have made it here.");
            return f.createExprType(Expr.INT);
        } else if (type instanceof Type.Array) {
            return f.createExprType(Expr.INT);
            //} else if (type instanceof Type.Null) {
            //return f.createExprType(Expr.INT);
        } else if (type instanceof Type.Byte) {
            return f.createExprType(Expr.INT);
        }
        //Todo throw error
        System.out.println(type);
        throw new Error("Some error to be decided later.");
    }

    private ExprElement.Value writeConstantValue(Constant constant) {
        if (constant.type().equals(Type.T_INT)) {
            return f.createValue(new Integer(constant.toString()));
        } else if (constant.type().equals(Type.T_BOOL)) {
            if ("true".equals(constant.toString())) {
                return f.createValue(TRUE);
            } else {
                return f.createValue(FALSE);
            }
            //} if (constant.type() instanceof Type.Null) {
            //return f.createValue(0);
//        } if (constant.type() instanceof Type.Byte) {
//            System.out.println(constant.toString());
//            return f.createValue(0);
        }
        System.out.println(constant);
        System.out.println(constant.type());
        //Todo throw error
        throw new Error("Some error to be decided later.");
    }

    /**
     * Creates AST Expr for Binary operator.
     *
     * Uses target, type and, operand 1 and 2 to create the loading
     * of the operand and the storing to the target.
     *
     *
     * @param c - Binary Operator.
     * @return - Expr representing the Binary Operator.
     */
    private Expr write(Codes.BinaryOperator c) {
        //TODO: add the ability to have more targets
        return f.createSetLocal(
                f.createVar("$" + c.target(0)),
                f.createBinOp(f.createExprType(getType(c.type(0))),
                        getOp(c.opcode()),
                        f.createGetLocal(
                                f.createVar("$" + c.operand(0))),
                        f.createGetLocal(
                                f.createVar("$" + c.operand(1)))));
    }

    /**
     * Creates AST Expr for Return.
     *
     * Uses operand, if its 0 then do no work, else load the value and
     * return it.
     *
     *
     * @param c - Return
     * @return - Expr representing Return.
     */
    private Expr write(Codes.Return c) {
        if (c.operands().length == 0) {
            return null;//TODO: add something in here for if the based on return values needed.
        } else {
            return f.createReturn(f.createGetLocal(
                    f.createVar("$" + c.operand(0))));
        }
    }

    /**
     * Creates AST Expr for Invoke.
     *
     * Uses function name and operand list to create a function call that
     * includes all operands.
     *
     *
     * @param c - Invoke
     * @return - Expr representing Invoke.
     */
    private Expr write(Codes.Invoke c) {//TODO:Make it so that functions can call functions from other files.
        //List<Type> parameterTypes = c.type(0).params();
        List<Expr> exprs = new ArrayList<>();

        for (int operand : c.operands()) {
            exprs.add(f.createGetLocal(
                    f.createVar("$" + operand)
            ));
        }
        if (c.targets().length <= 0) {
            return f.createCall(
                    f.createVar("$" + c.name.name()),
                    exprs
            );
        } else {
            return f.createSetLocal(
                    f.createVar("$" + c.target(0)),
                    f.createCall(
                            f.createVar("$" + c.name.name()),
                            exprs
                    )
            );
        }
    }

    //TODO: Remove un-need calls.
    //TODO: Another plan, add a type variable in for each var.

    /**
     * Creates a list of AST Local values.
     *
     * 
     * @param d
     * @param variableList
     * @return
     */
    private List<FunctionElement.Local> writeVariable(CodeBlock d, List<Integer> variableList) {
        List<FunctionElement.Local> locals = new ArrayList<>();
        labelMap.put(BASE_LABEL, labelNum++);
        int oldWasmLabelNumber = wasmLabelNumber;
        List<Code> codes = new ArrayList<>(d.bytecodes());
        for (int i = 0; i < codes.size(); i++) {
            Code bytecode = codes.get(i);
            if (bytecode instanceof Codes.ArrayGenerator) {
                writeVariable((Codes.ArrayGenerator) bytecode, variableList).forEach(locals::add);
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
                codes.remove(bytecode);
                Codes.Loop a = (Codes.Loop) bytecode;
                String label = getLabel();
                codes.add(i, Codes.Label(label));
                int temp = i + 1;
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

    private List<FunctionElement.Local> writeVariable(Codes.ArrayGenerator bytecode, List<Integer> variableList) {
        List<FunctionElement.Local> locals = new ArrayList<>();
        getLabel(); //Ensures that this label is available at this point in time.
        locals.add(f.createLocal(getVar(), f.createExprType(Expr.INT)));
        if (!variableList.contains(bytecode.target(0))) {
            variableList.add(bytecode.target(0));
            locals.add(f.createLocal("$" + bytecode.target(0),
                    f.createExprType(getType(bytecode.type(0)))));
            locals.add(f.createLocal("$" + WasmFileWriter.TYPE_VAR_NAME + bytecode.target(0),
                    f.createExprType(WasmFileWriter.TYPE_VAR_TYPE)));
        }
        return locals;
    }

    private List<FunctionElement.Local> writeVariable(Codes.FieldLoad bytecode, List<Integer> variableList) {
        List<FunctionElement.Local> locals = new ArrayList<>();
        if (!variableList.contains(bytecode.target(0))) {
            variableList.add(bytecode.target(0));
            locals.add(f.createLocal("$" + bytecode.target(0),
                    f.createExprType(getType(bytecode.fieldType()))));
            locals.add(f.createLocal("$" + WasmFileWriter.TYPE_VAR_NAME + bytecode.target(0),
                    f.createExprType(WasmFileWriter.TYPE_VAR_TYPE)));
        }
        return locals;
    }

    private List<FunctionElement.Local> writeVariable(Codes.NewRecord bytecode, List<Integer> variableList) {
        List<FunctionElement.Local> locals = new ArrayList<>();
        if (!variableList.contains(bytecode.target(0))) {
            variableList.add(bytecode.target(0));
            locals.add(f.createLocal("$" + bytecode.target(0),
                    f.createExprType(getType(bytecode.type(0)))));
            locals.add(f.createLocal("$" + WasmFileWriter.TYPE_VAR_NAME + bytecode.target(0),
                    f.createExprType(WasmFileWriter.TYPE_VAR_TYPE)));
        }
        return locals;
    }

    private List<FunctionElement.Local> writeVariable(Codes.UnaryOperator bytecode, List<Integer> variableList) {
        List<FunctionElement.Local> locals = new ArrayList<>();
        if (!variableList.contains(bytecode.target(0))) {
            variableList.add(bytecode.target(0));
            locals.add(f.createLocal("$" + bytecode.target(0),
                    f.createExprType(getType(bytecode.type(0)))));
            locals.add(f.createLocal("$" + WasmFileWriter.TYPE_VAR_NAME + bytecode.target(0),
                    f.createExprType(WasmFileWriter.TYPE_VAR_TYPE)));
        }
        return locals;
    }

    private List<FunctionElement.Local> writeVariable(Codes.If bytecode, List<Integer> variableList) {
        List<FunctionElement.Local> locals = new ArrayList<>();
        if (isArray(bytecode.type(0))) {
            getLabel(); //TODO: create a better solution than this.
            locals.add(f.createLocal(getVar(), f.createExprType(Expr.INT)));
        } else if (isRecord(bytecode.type(0))) {
            getLabel(); //TODO: create a better solution than this.
            locals.add(f.createLocal(getVar(), f.createExprType(Expr.INT)));
        }
        return locals;
    }

    private void addToLocal(List<FunctionElement.Local> locals, FunctionElement.Local local) {
        if (local != null) {
            locals.add(local);
        }
    }

    private List<FunctionElement.Local> writeVariable(Codes.IndexOf bytecode, List<Integer> variableList) {
        List<FunctionElement.Local> locals = new ArrayList<>();
        if (!variableList.contains(bytecode.target(0))) {
            variableList.add(bytecode.target(0));
            locals.add(f.createLocal("$" + bytecode.target(0),
                    f.createExprType(getType(bytecode.type(0).element()))));
            locals.add(f.createLocal("$" + WasmFileWriter.TYPE_VAR_NAME + bytecode.target(0),
                    f.createExprType(WasmFileWriter.TYPE_VAR_TYPE)));
        }
        return locals;
    }

    private List<FunctionElement.Local> writeVariable(Codes.LengthOf bytecode, List<Integer> variableList) {
        List<FunctionElement.Local> locals = new ArrayList<>();
        if (!variableList.contains(bytecode.target(0))) {
            variableList.add(bytecode.target(0));
            locals.add(f.createLocal("$" + bytecode.target(0),
                    f.createExprType(Expr.INT)));
            locals.add(f.createLocal("$" + WasmFileWriter.TYPE_VAR_NAME + bytecode.target(0),
                    f.createExprType(WasmFileWriter.TYPE_VAR_TYPE)));
        }
        return locals;
    }

    private List<FunctionElement.Local> writeVariable(Codes.NewArray bytecode, List<Integer> variableList) {
        List<FunctionElement.Local> locals = new ArrayList<>();
        if (!variableList.contains(bytecode.target(0))) {
            variableList.add(bytecode.target(0));
            locals.add(f.createLocal("$" + bytecode.target(0),
                    f.createExprType(Expr.INT)));
            locals.add(f.createLocal("$" + WasmFileWriter.TYPE_VAR_NAME + bytecode.target(0),
                    f.createExprType(WasmFileWriter.TYPE_VAR_TYPE)));
        }
        return locals;
    }

    private List<FunctionElement.Local> writeVariable(Codes.Assign bytecode, List<Integer> variableList) {
        List<FunctionElement.Local> locals = new ArrayList<>();
        if (isArray(bytecode.type(0))) {
//			getLabel();
//			locals.add(f.createLocal(getVar(), f.createExprType(Expr.INT)));
        }
        if (!variableList.contains(bytecode.target(0))) {
            variableList.add(bytecode.target(0));
            locals.add(f.createLocal("$" + bytecode.target(0),
                    f.createExprType(getType(bytecode.type(0)))));
            locals.add(f.createLocal("$" + WasmFileWriter.TYPE_VAR_NAME + bytecode.target(0),
                    f.createExprType(WasmFileWriter.TYPE_VAR_TYPE)));
        }
        return locals;
    }

    /**
     * Writes a local variable of an invocation call and adds it to the list of made vars if not
     * there.
     */
    private List<FunctionElement.Local> writeVariable(Codes.Invoke bytecode, List<Integer> variableList) {
        List<FunctionElement.Local> locals = new ArrayList<>();
        if (bytecode.targets().length <= 0 || variableList.contains(bytecode.target(0))) {
            return locals;
        } else {
            variableList.add(bytecode.target(0));
        }
        List<Type> targets = bytecode.type(0).returns();
        //returnMap.get(bytecode.name.name());

        Type targetType = targets.get(0); //FIXME: Might be using incorrect target.

        locals.add(f.createLocal("$" + bytecode.target(0),
                f.createExprType(getType(targetType))));
        locals.add(f.createLocal("$" + WasmFileWriter.TYPE_VAR_NAME + bytecode.target(0),
                f.createExprType(WasmFileWriter.TYPE_VAR_TYPE)));
        return locals;
    }

    /**
     * Writes a local variable that is used in a constant call.
     */
    private List<FunctionElement.Local> writeVariable(Codes.Const bytecode, List<Integer> variableList) {
        List<FunctionElement.Local> locals = new ArrayList<>();
        if (!variableList.contains(bytecode.target(0))) {
            variableList.add(bytecode.target(0));
            locals.add(f.createLocal("$" + bytecode.target(),
                    f.createExprType(getType(bytecode.constant.type()))));
            locals.add(f.createLocal("$" + WasmFileWriter.TYPE_VAR_NAME + bytecode.target(0),
                    f.createExprType(WasmFileWriter.TYPE_VAR_TYPE)));
        }
        return locals;
    }

    /**
     * Looks at a list of variable if its not there will make the list variable and add it to the
     * list.
     */
    private List<FunctionElement.Local> writeVariable(Codes.BinaryOperator bytecode, List<Integer> variableList) {
        List<FunctionElement.Local> locals = new ArrayList<>();
        if (!variableList.contains(bytecode.target(0))) {
            variableList.add(bytecode.target(0));
            locals.add(f.createLocal("$" + bytecode.target(0),
                    f.createExprType(getType(bytecode.type(0)))));
            locals.add(f.createLocal("$" + WasmFileWriter.TYPE_VAR_NAME + bytecode.target(0),
                    f.createExprType(WasmFileWriter.TYPE_VAR_TYPE)));
        }
        //TODO:Work with all the types - assumption first is target type possible get type method.
        return locals;
    }

    /*
    private Expr createMemoryCopy(int location, int placement) { //TODO: Work out the parameters and there types.
        List<Expr> exprs = new ArrayList<>();


        List<Expr> then = new ArrayList<>();
        List<Expr> andThen = new ArrayList<>();
        List<Expr> alt = new ArrayList<>();
        List<Expr> funParams = new ArrayList<>();


        andThen.add( // If its not equal to both then it will just copy the values.
                f.createStore(
                        f.createExprType(Expr.INT),
                        (placement * 8),
                        null,
                        f.createGetLocal(
                                f.createVar("$newBase") //TODO: Create a variable for this.
                        ),
                        f.createLoad(
                                f.createExprType(Expr.INT),
                                null,
                                (placement * 8),
                                null,
                                f.createGetLocal(
                                        f.createVar("$" + location)
                                )
                        )
                )
        );


        funParams.add( // Loads the location of the array to be copied.
                f.createLoad(
                        f.createExprType(Expr.INT),
                        null,
                        (placement * 8),
                        null,
                        f.createGetLocal(
                                f.createVar("$" + location)
                        )
                )
        );

        alt.add( // Calls a function if equal to eather the array type or record type.
                f.createStore(
                        f.createExprType(Expr.INT),
                        (placement * 8),
                        null,
                        f.createGetLocal(
                                f.createVar("$newBase") //TODO: Create a veriable for this.
                        ),
                        f.createCall(
                                f.createVar("$DeepMemoryCopy"),
                                funParams
                        )

                )
        );

        then.add( // Checks if its a record if its not then it will finally copy normally else it will recursive copy.
                f.createIf(
                        f.createRelOp(
                                f.createExprType(Expr.INT),
                                Expr.NE,
                                f.createLoad(
                                        f.createExprType(Expr.INT),
                                        null,
                                        null,
                                        null,
                                        f.createBinOp(
                                                f.createExprType(Expr.INT),
                                                Expr.add,
                                                f.createGetLocal(
                                                        f.createVar("$" + location) //TODO: Parameter
                                                ),
                                                f.createBinOp(
                                                        f.createExprType(Expr.INT),
                                                        Expr.add,
                                                        f.createBinOp(
                                                                f.createExprType(Expr.INT),
                                                                Expr.mul,
                                                                f.createGetLocal(
                                                                        f.createVar("$inc") //TODO: Organise a veriable for this.
                                                                ),
                                                                f.createConst(
                                                                        f.createExprType(Expr.INT),
                                                                        f.createValue(8)
                                                                )
                                                        ),
                                                        f.createConst(
                                                                f.createExprType(Expr.INT),
                                                                f.createValue(4)
                                                        )
                                                )
                                        )
                                ),
                                f.createConst(
                                        f.createExprType(Expr.INT),
                                        f.createValue(typeMap.get("record"))
                                )
                        ),
                        null,
                        andThen,
                        null,
                        alt
                )
        );


        exprs.add( // Checks if the its not equal to a array. If is then recursive copy.
                f.createIf(
                        f.createRelOp(
                                f.createExprType(Expr.INT),
                                Expr.NE,
                                f.createLoad(
                                        f.createExprType(Expr.INT),
                                        null,
                                        null,
                                        null,
                                        f.createBinOp(
                                                f.createExprType(Expr.INT),
                                                Expr.add,
                                                f.createGetLocal(
                                                        f.createVar("$" + location) //TODO: Parameter
                                                ),
                                                f.createBinOp(
                                                        f.createExprType(Expr.INT),
                                                        Expr.add,
                                                        f.createBinOp(
                                                                f.createExprType(Expr.INT),
                                                                Expr.mul,
                                                                f.createGetLocal(
                                                                        f.createVar("$inc") //TODO: Organise a value for this.
                                                                ),
                                                                f.createConst(
                                                                        f.createExprType(Expr.INT),
                                                                        f.createValue(8)
                                                                )
                                                        ),
                                                        f.createConst(
                                                                f.createExprType(Expr.INT),
                                                                f.createValue(4)
                                                        )
                                                )
                                        )
                                ),
                                f.createConst(
                                        f.createExprType(Expr.INT),
                                        f.createValue(typeMap.get("array"))
                                )
                        ),
                        null,
                        then,
                        null,
                        alt
                )
        );

        return f.createBlock(null, exprs);
    }
    */

    private Function createMemoryCopyHelperFunction() {
        List<FunctionElement.Param> params = new ArrayList<>();
        FunctionElement.Result result = f.createResult(f.createExprType(Expr.INT));
        List<FunctionElement.Local> locals = new ArrayList<>();
        List<Expr> mainBlock = new ArrayList<>();

        // Work out what parameters are need.
        params.add(
                f.createParam("$location", f.createExprType(Expr.INT))
        );

        // Work out the local variables needed.
        locals.add(
                f.createLocal("$length", f.createExprType(Expr.INT))
        );

        locals.add(
                f.createLocal("$newBase", f.createExprType(Expr.INT))
        );

        locals.add(
                f.createLocal("$inc", f.createExprType(Expr.INT))
        );

        // Create the main block of code.

        List<Expr> exprs = new ArrayList<>();

        // Create a memory allocation of Size

        exprs.add( //Loads the length for later use.
                f.createSetLocal(
                        f.createVar("$length"),
                        f.createLoad(
                                f.createExprType(Expr.INT),
                                null,
                                null,
                                null,
                                f.createGetLocal(
                                        f.createVar("$location")
                                )
                        )
                )
        );

        exprs.add( //Stores the old location into a new storage location.
                f.createSetLocal(
                        f.createVar("$newBase"),
                        f.createLoad(
                                f.createExprType(Expr.INT),
                                null,
                                null,
                                null,
                                f.createConst(
                                        f.createExprType(Expr.INT),
                                        f.createValue(BASE_MEMORY_LOCATION)
                                )
                        )
                )
        );

        exprs.add( //Sets base to the point at a avalible memory location..
                f.createStore(
                        f.createExprType(Expr.INT),
                        null,
                        null,
                        f.createConst(f.createExprType(Expr.INT), f.createValue(BASE_MEMORY_LOCATION)),
                        f.createBinOp(
                                f.createExprType(Expr.INT),
                                Expr.add,
                                f.createLoad(
                                        f.createExprType(Expr.INT),
                                        null,
                                        null,
                                        null,
                                        f.createConst(f.createExprType(Expr.INT), f.createValue(BASE_MEMORY_LOCATION))
                                ),
                                        f.createBinOp(
                                                f.createExprType(Expr.INT),
                                                Expr.mul,
                                                f.createConst(
                                                        f.createExprType(Expr.INT),
                                                        f.createValue(8)
                                                ),
                                                f.createBinOp(
                                                        f.createExprType(Expr.INT),
                                                        Expr.add,
                                                        f.createGetLocal(
                                                                f.createVar("$length")
                                                        ),
                                                        f.createConst(
                                                                f.createExprType(Expr.INT),
                                                                f.createValue(1)
                                                        )
//                                                )
                                        )
                                )
                        )
                )
        );

        exprs.add( //Stores the length.
                f.createStore(
                        f.createExprType(Expr.INT),
                        null,
                        null,
                        f.createGetLocal(
                                f.createVar("$newBase")
                        ),
                        f.createGetLocal(
                                f.createVar("$length")
                        )
                )
        );

        exprs.add(
                f.createStore(
                        f.createExprType(Expr.INT),
                        (4),
                        null,
                        f.createGetLocal(
                                f.createVar("$newBase")
                        ),
                        f.createLoad(
                                f.createExprType(Expr.INT),
                                null,
                                (4),
                                null,
                                f.createGetLocal(
                                        f.createVar("$location") //TODO: Organise parameter for this.
                                )
                        )
                )
        );

        // For each element check its type.

        exprs.add(f.createSetLocal(
                f.createVar("$inc"),
                f.createConst(
                        f.createExprType(Expr.INT),
                        f.createValue(1)
                )
        ));

        // If it is a pointer then recursively call this function.

        List<Expr> then = new ArrayList<>();
        List<Expr> andThen = new ArrayList<>();
        List<Expr> alt = new ArrayList<>();
        List<Expr> funParams = new ArrayList<>();
        List<Expr> loopContents = new ArrayList<>();


        andThen.add( // If its not equal to both then it will just copy the values.
                f.createStore(
                        f.createExprType(Expr.INT),
                        null,
                        null,
                        f.createBinOp(
                                f.createExprType(Expr.INT),
                                Expr.add,
                                f.createGetLocal(
                                        f.createVar("$newBase")
                                ),
                                f.createBinOp(
                                        f.createExprType(Expr.INT),
                                        Expr.mul,
                                        f.createGetLocal(
                                                f.createVar("$inc")
                                        ),
                                        f.createConst(
                                                f.createExprType(Expr.INT),
                                                f.createValue(8)
                                        )
                                )
                        ),
                        f.createLoad(
                                f.createExprType(Expr.INT),
                                null,
                                null,
                                null,
                                f.createBinOp(
                                        f.createExprType(Expr.INT),
                                        Expr.add,
                                        f.createGetLocal(
                                                f.createVar("$location") //TODO: Parameter
                                        ),
                                        f.createBinOp(
                                                f.createExprType(Expr.INT),
                                                Expr.mul,
                                                f.createGetLocal(
                                                        f.createVar("$inc")
                                                ),
                                                f.createConst(
                                                        f.createExprType(Expr.INT),
                                                        f.createValue(8)
                                                )
                                        )
                                )
                        )
                )
        );


        funParams.add( // Loads the location of the array to be copied.
                f.createLoad(
                        f.createExprType(Expr.INT),
                        null,
                        null,
                        null,
                        f.createBinOp(
                                f.createExprType(Expr.INT),
                                Expr.add,
                                f.createGetLocal(
                                        f.createVar("$location") //TODO: Parameter
                                ),
                                f.createBinOp(
                                        f.createExprType(Expr.INT),
                                        Expr.mul,
                                        f.createGetLocal(
                                                f.createVar("$inc")
                                        ),
                                        f.createConst(
                                                f.createExprType(Expr.INT),
                                                f.createValue(8)
                                        )
                                )
                        )
                )
        );

        alt.add( // Calls a function if equal to eather the array type or record type.
                f.createStore(
                        f.createExprType(Expr.INT),
                        null,
                        null,
                        f.createBinOp(
                                f.createExprType(Expr.INT),
                                Expr.add,
                                f.createGetLocal(
                                        f.createVar("$newBase")
                                ),
                                f.createBinOp(
                                        f.createExprType(Expr.INT),
                                        Expr.mul,
                                        f.createGetLocal(
                                                f.createVar("$inc")
                                        ),
                                        f.createConst(
                                                f.createExprType(Expr.INT),
                                                f.createValue(8)
                                        )
                                )
                        ),
                        f.createCall(
                                f.createVar("$DeepMemoryCopy"),
                                funParams
                        )

                )
        );

        then.add( // Checks if its a record if its not then it will finally copy normally else it will recursive copy.
                f.createIf(
                        f.createRelOp(
                                f.createExprType(Expr.INT),
                                Expr.NE,
                                f.createLoad(
                                        f.createExprType(Expr.INT),
                                        null,
                                        null,
                                        null,
                                        f.createBinOp(
                                                f.createExprType(Expr.INT),
                                                Expr.add,
                                                f.createGetLocal(
                                                        f.createVar("$location") //TODO: Parameter
                                                ),
                                                f.createBinOp(
                                                        f.createExprType(Expr.INT),
                                                        Expr.add,
                                                        f.createBinOp(
                                                                f.createExprType(Expr.INT),
                                                                Expr.mul,
                                                                f.createGetLocal(
                                                                        f.createVar("$inc")
                                                                ),
                                                                f.createConst(
                                                                        f.createExprType(Expr.INT),
                                                                        f.createValue(8)
                                                                )
                                                        ),
                                                        f.createConst(
                                                                f.createExprType(Expr.INT),
                                                                f.createValue(4)
                                                        )
                                                )
                                        )
                                ),
                                f.createConst(
                                        f.createExprType(Expr.INT),
                                        f.createValue(typeMap.get("record"))
                                )
                        ),
                        null,
                        andThen,
                        null,
                        alt
                )
        );

        loopContents.add( // Checks if the its not equal to a array. If is then recursive copy.
                f.createIf(
                        f.createRelOp(
                                f.createExprType(Expr.INT),
                                Expr.NE,
                                f.createLoad(
                                        f.createExprType(Expr.INT),
                                        null,
                                        null,
                                        null,
                                        f.createBinOp(
                                                f.createExprType(Expr.INT),
                                                Expr.add,
                                                f.createGetLocal(
                                                        f.createVar("$location") //TODO: Parameter
                                                ),
                                                f.createBinOp(
                                                        f.createExprType(Expr.INT),
                                                        Expr.add,
                                                        f.createBinOp(
                                                                f.createExprType(Expr.INT),
                                                                Expr.mul,
                                                                f.createGetLocal(
                                                                        f.createVar("$inc")
                                                                ),
                                                                f.createConst(
                                                                        f.createExprType(Expr.INT),
                                                                        f.createValue(8)
                                                                )
                                                        ),
                                                        f.createConst(
                                                                f.createExprType(Expr.INT),
                                                                f.createValue(4)
                                                        )
                                                )
                                        )
                                ),
                                f.createConst(
                                        f.createExprType(Expr.INT),
                                        f.createValue(typeMap.get("array"))
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

        cont.add(f.createBr(f.createVar("$loop"), null));

        Expr.If loopContinue = f.createIf( //Checks if it will loop again and then loops.
                f.createRelOp(
                        f.createExprType(Expr.INT),
                        Expr.LE,
                        f.createGetLocal(
                                f.createVar("$inc")
                        ),
                        f.createGetLocal(
                                f.createVar("$length")
                        )
                ),
                null,
                cont,
                null,
                null
        );

        // Else copy the value.

        Expr.SetLocal increment = f.createSetLocal( // Increments the i Varable.
                f.createVar("$inc"),
                f.createBinOp(
                        f.createExprType(Expr.INT),
                        Expr.add,
                        f.createGetLocal(
                                f.createVar("$inc")
                        ),
                        f.createConst(
                                f.createExprType(Expr.INT),
                                f.createValue(1)
                        )
                )
        );
        // Copy type values.

        loopContents.add( // Copys the type from one place to another.
                f.createStore(
                        f.createExprType(Expr.INT),
                        null,
                        null,
                        f.createBinOp(
                                f.createExprType(Expr.INT),
                                Expr.add,
                                f.createGetLocal(
                                        f.createVar("$newBase")
                                ),
                                f.createBinOp(
                                        f.createExprType(Expr.INT),
                                        Expr.add,
                                        f.createBinOp(
                                                f.createExprType(Expr.INT),
                                                Expr.mul,
                                                f.createGetLocal(
                                                        f.createVar("$inc")
                                                ),
                                                f.createConst(
                                                        f.createExprType(Expr.INT),
                                                        f.createValue(8)
                                                )
                                        ),
                                        f.createConst(
                                                f.createExprType(Expr.INT),
                                                f.createValue(4)
                                        )
                                )
                        ),
                        f.createLoad(
                                f.createExprType(Expr.INT),
                                null,
                                null,
                                null,
                                f.createBinOp(
                                        f.createExprType(Expr.INT),
                                        Expr.add,
                                        f.createGetLocal(
                                                f.createVar("$location") //TODO: Parameter
                                        ),
                                        f.createBinOp(
                                                f.createExprType(Expr.INT),
                                                Expr.add,
                                                f.createBinOp(
                                                        f.createExprType(Expr.INT),
                                                        Expr.mul,
                                                        f.createGetLocal(
                                                                f.createVar("$inc")
                                                        ),
                                                        f.createConst(
                                                                f.createExprType(Expr.INT),
                                                                f.createValue(8)
                                                        )
                                                ),
                                                f.createConst(
                                                        f.createExprType(Expr.INT),
                                                        f.createValue(4)
                                                )
                                        )
                                )
                        )
                )
        );

        loopContents.add(increment);
        loopContents.add(loopContinue);

        //Create entry if
        List<Expr> temp = new ArrayList<>();


        // return the pointer value.
        temp.add(
                f.createLoop(
                        "$loop",
                        null,
                        loopContents
                )
        );

        exprs.add(
                f.createIf(
                        f.createRelOp(
                                f.createExprType(Expr.INT),
                                Expr.LE,
                                f.createGetLocal(
                                        f.createVar("$inc")
                                ),
                                f.createGetLocal(
                                        f.createVar("$length")
                                )
                        ),
                        null,
                        temp,
                        null,
                        null
                )
        );

        exprs.add(
                f.createReturn(
                        f.createGetLocal(
                                f.createVar("$newBase")
                        )
                )
        );

        mainBlock.add(f.createBlock(null, exprs));

        return f.createFunction("$DeepMemoryCopy", null, params, result, locals, mainBlock);
    }


    private int getFieldLevel(Type.EffectiveRecord type, String field) {
        List<String> fields = new ArrayList<>(type.fields().keySet());

        fields.sort(String::compareTo);

        return fields.indexOf(field);
    }

    private Integer getMetaType(Type t) {
        if (isArray(t)) {
            return typeMap.get("array");
        } else if (isRecord(t)) {
            return typeMap.get("record"); //TODO: Make a type name that cant be copyed.
        } else { //TODO: Add record info here.
            if (typeMap.containsKey(t.toString())) {
                return typeMap.get(t.toString());
            } else {
                typeMap.put(t.toString(), typeNum++);
                return typeMap.get(t.toString());
            }
        }
    }

    private Integer getRecordMetaType(Type t) {
        if (typeMap.containsKey(t.toString())) {
            return typeMap.get(t.toString());
        } else {
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
     */
    private String getType(Type t) {
        if (t.equals(Type.T_INT)) {
            return Expr.INT;
        } else if (t.equals(Type.T_BOOL)) {
            return BOOL;
        } else if (t.equals(Type.T_ARRAY_ANY)) {
            return Expr.INT;
        } else if (t.equals(Type.T_ANY)) {
            return Expr.INT;
        }
        return "i32"; //TODO: throw a error but returning in for the mine time due to more likely value.
    }

    /**
     * For getting op codes TODO: Maby add to a map.
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
        String label = DEFAULT_LABEL_NAME + (wasmLabelNumber++);
        //labelMap.put(label, labelNum++); Not related to how labels work.
        return label;
    }

    private String getVar() {
        String var = DEFAULT_VAR_NAME + (wasmVarNumber++);
        return var;
    }
}
