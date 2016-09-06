package util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ast.Expr;
import wywasm.WasmFileWriter;

/**
 * Created by Shane on 6/09/16.
 */
public class WyILWasmFactory {

    private final WastFactory factory;

    public WyILWasmFactory (WastFactory factory) {
        this.factory = factory;
    }

    /**
     * Used for assigning the length of a list.
     */
    public Expr createConstructLengthAssignment(int length, int type, int target) {
        return createConstructLengthAssignment(
                factory.createConst(
                        factory.createExprType(Expr.INT),
                        factory.createValue(length)
                ),
                factory.createConst(
                        factory.createExprType(WasmFileWriter.TYPE_VAR_TYPE),
                        factory.createValue(type)
                ),
                factory.createGetLocal(
                        factory.createVar("$" + target)
                )
        );
    }

    public Expr createConstructLengthAssignment(Expr length, Expr type, Expr target) {
        List<Expr> exprs = new ArrayList<>();
        exprs.add(
                createStore(
                        target,
                        0,
                        length
                )
        );
        exprs.add(
                createStore(
                        target,
                        4,
                        type
                )
        );
        return factory.createBlock(null, exprs);

    }

    /**
     * Creates a Expr that assigns a pointer to the target verable with appreate type.
     */
    public Expr createPointerAssignment(int target, int type) {
        List<Expr> exprs = new ArrayList<>();
        exprs.add(factory.createSetLocal(
                factory.createVar("$" + target),
                factory.createLoad(
                        factory.createExprType(Expr.INT),
                        null,
                        null,
                        null,
                        factory.createConst(
                                factory.createExprType(Expr.INT),
                                factory.createValue(WasmFileWriter.BASE_MEMORY_LOCATION)
                        )
                )
        ));

        exprs.add(
                factory.createSetLocal(
                        factory.createVar("$" + WasmFileWriter.TYPE_VAR_NAME + target),
                        factory.createConst(
                                factory.createExprType(WasmFileWriter.TYPE_VAR_TYPE),
                                factory.createValue(type) //TODO: Sort out records here,
                        )
                )
        );

        return factory.createBlock(null, exprs);
    }

    /**
     * Assigns a new pointer to the base pointer.
     */
    public Expr createBaseAddressAssignment(int distanceFromOldToNew) {
        return createBaseAddressAssignment(
                factory.createConst(
                        factory.createExprType(Expr.INT),
                        factory.createValue(distanceFromOldToNew)
                )
        );
    }

    /**
     * Assigns a new location to the base address pointer location.
     *
     * @param distanceFromOldToNew - Expression that represents the distance to change by.
     * @return - Expression that represents the storing of the of a modifies base address in memory.
     */
    public Expr createBaseAddressAssignment(Expr distanceFromOldToNew) {

        return createStore(
                factory.createConst(factory.createExprType(Expr.INT), factory.createValue(WasmFileWriter.BASE_MEMORY_LOCATION)),
                0,
                factory.createBinOp(
                        factory.createExprType(Expr.INT),
                        Expr.add,
                        createLoad(
                                factory.createConst(
                                        factory.createExprType(Expr.INT),
                                        factory.createValue(WasmFileWriter.BASE_MEMORY_LOCATION)
                                ),
                                0
                        ),
                        distanceFromOldToNew
                )

        );
    }

    /**
     * Code for Initializing the base addressing zone needs to be used when maing a new record or
     * array.
     */
    public Expr createBaseAddressInit() {
        List<Expr> then = new ArrayList<>();

        then.add(
                factory.createStore(
                        factory.createExprType(Expr.INT),
                        null,
                        null,
                        factory.createConst(factory.createExprType(Expr.INT),
                                factory.createValue(WasmFileWriter.BASE_MEMORY_LOCATION)),
                        factory.createConst(factory.createExprType(Expr.INT),
                                factory.createValue(WasmFileWriter.BASE_MEMORY_VALUE))
                )
        );

        return factory.createIf(
                factory.createRelOp(
                        factory.createExprType(Expr.INT),
                        Expr.EQ,
                        factory.createConst(
                                factory.createExprType(Expr.INT),
                                factory.createValue(WasmFileWriter.BASE_MEMORY_INCORRECT_VALUE)
                        ),
                        factory.createLoad(
                                factory.createExprType(Expr.INT),
                                null,
                                null,
                                null,
                                factory.createConst(
                                        factory.createExprType(Expr.INT),
                                        factory.createValue(WasmFileWriter.BASE_MEMORY_LOCATION)
                                )
                        )
                ),
                null,
                then,
                null,
                null
        );
    }

    public Expr createCompareAndDeepCopyCall(Expr operand, Expr type, Map<String, Integer> typeMap) {
        List<Expr> exprs = new ArrayList<>();
        //Check the type and act accordingly.

        List<Expr> paramters = new ArrayList<>();

        paramters.add(
                operand
        );

        //If array then deep memory copy.
        Expr arrayCondition =
                createCompareTypes(
                        factory.createConst(
                                factory.createExprType(Expr.INT),
                                factory.createValue(typeMap.get("array"))
                        ),
                        type
                );

        //Contains statements for if it is a array.
        List<Expr> ifArray = new ArrayList<>();
        ifArray.add(
                factory.createCall(
                        factory.createVar("$DeepMemoryCopy"),
                        paramters
                )
        );

        //Stores values about not being in a array.
        //Contains a if related to checking records.
        List<Expr> ifNotArray = new ArrayList<>();

        //If record then deep memory copy.

        Expr recordCondition =
                createCompareTypes(
                        factory.createConst(
                                factory.createExprType(Expr.INT),
                                factory.createValue(typeMap.get("record"))
                        ),
                        type
                );

        List<Expr> ifRecord = new ArrayList<>();
        ifRecord.add(
                factory.createCall(
                        factory.createVar("$DeepMemoryCopy"),
                        paramters
                )
        );
        List<Expr> ifNotRecord = new ArrayList<>();
        ifNotRecord.add(
                operand
        );

        //Else copy normally.


        //If it is not a array check if it is a record.
        ifNotArray.add(
                factory.createIf(
                        recordCondition,
                        null,
                        ifRecord,
                        null,
                        ifNotRecord
                )
        );

        //Check first if it is array else run not an array.
        exprs.add(
                factory.createIf(
                        arrayCondition,
                        null,
                        ifArray,
                        null,
                        ifNotArray
                )
        );
        //System.out.println("Running new function");
        return factory.createBlock(null, exprs);
    }

    public Expr createCompareTypes(Expr typeOne, Expr typeTwo) {
        return factory.createRelOp(
                factory.createExprType(Expr.INT),
                Expr.EQ,
                typeOne,
                typeTwo
        );
    }

    public Expr createStore(Expr location, int offset, Expr value) {
        return factory.createStore(
                factory.createExprType(Expr.INT),
                offset,
                null,
                location,
                value
        );
    }

    public Expr createLoad(Expr location, int offset) {
        return factory.createLoad(
                factory.createExprType(Expr.INT),
                null,
                offset,
                null,
                location
        );
    }

    public Expr createLoop(Expr setup, Expr condition, Expr body, Expr increment, String label) {
        List<Expr> exprs = new ArrayList<>();

        exprs.add(setup); //Sets up for the loop body. May be a empty block.

        String loopName = label;
        List<Expr> loopBody = new ArrayList<>();
        List<Expr> ifBody = new ArrayList<>();

        ifBody.add(body);
        ifBody.add(increment);
        ifBody.add(
                factory.createBr(
                        factory.createVar("$" + loopName),
                        null
                )
        );

        //Creating the loop body
        //Has a if statement that checks a conditional.
        loopBody.add(
                factory.createIf(
                        condition,
                        null,
                        ifBody,
                        null,
                        null
                )
        );

        //Add loop to the list of expressions in the block.
        exprs.add(
                factory.createLoop(
                        "$" + loopName,
                        null,
                        loopBody
                )
        );

        return factory.createBlock(null, exprs);
    }

    public Expr createMemoryValueAndTypeStore(Expr location, int offset, Expr value, Expr type, Map<String, Integer> typeMap) {
        List<Expr> exprs = new ArrayList<>();
        exprs.add(
                createStore(
                        location,
                        offset,
                        createCompareAndDeepCopyCall( //Checks and then calls deep memory copy if required.
                                value,
                                type,
                                typeMap
                        )
                )
        );
        exprs.add(
                createStore(
                        location,
                        offset + 4,
                        type
                )
        );
        return factory.createBlock(null, exprs);
    }

    public Expr.If createIf(Expr condition, Expr then, Expr els) {
        List<Expr> t = new ArrayList<>();
        t.add(then);
        List<Expr> e = null;
        if (els != null) {
            e = new ArrayList<>();
            e.add(els);
        }

        return factory.createIf(condition,null,t,null,e);
    }

}
