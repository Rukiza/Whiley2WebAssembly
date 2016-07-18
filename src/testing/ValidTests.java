package testing;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import util.WastFactory;
import wycc.util.Pair;
import wyil.io.WyilFileReader;
import wyil.lang.WyilFile;
import wywasm.WasmFileWriter;

import java.io.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.fail;

/**
 * Created by Shane on 8/04/16.
 */
@RunWith(Parameterized.class)
public class ValidTests {

    public final static Map<String, String> IGNORED = new HashMap<String, String>();

    static {
        IGNORED.put("Complex_Valid_3", "Issue ???");
        IGNORED.put("ConstrainedIntersection_Valid_1", "unknown");
        IGNORED.put("ConstrainedNegation_Valid_1", "#342");
        IGNORED.put("ConstrainedNegation_Valid_2", "#342");
        IGNORED.put("Contractive_Valid_1", "Issue ???");
        IGNORED.put("DoWhile_Valid_4", "unknown");
        IGNORED.put("FunctionRef_Valid_2", "Issue ???");
        IGNORED.put("FunctionRef_Valid_13", "#555");
        IGNORED.put("Import_Valid_4", "#492");
        IGNORED.put("Import_Valid_5", "#492");
        IGNORED.put("Intersection_Valid_1", "Issue ???");
        IGNORED.put("Intersection_Valid_2", "Issue ???");
        IGNORED.put("Lifetime_Lambda_Valid_4", "#641");
        IGNORED.put("ListAccess_Valid_6", "Issue ???");
        IGNORED.put("ListAccess_Valid_7", "Issue ???");
        IGNORED.put("NegationType_Valid_3", "Issue ???");
        IGNORED.put("OpenRecord_Valid_11", "#585");
        IGNORED.put("RecordCoercion_Valid_1", "#564");
        IGNORED.put("RecordSubtype_Valid_1", "Issue ???");
        IGNORED.put("RecordSubtype_Valid_2", "Issue ???");
        IGNORED.put("RecursiveType_Valid_12", "#339");
        IGNORED.put("RecursiveType_Valid_22", "#339");
        IGNORED.put("RecursiveType_Valid_28", "#364");
        IGNORED.put("RecursiveType_Valid_3", "#406");
        IGNORED.put("RecursiveType_Valid_4", "#406");
        IGNORED.put("RecursiveType_Valid_5", "#18");
        IGNORED.put("Reference_Valid_6", "#553");
        IGNORED.put("TypeEquals_Valid_23", "Issue ???");
        IGNORED.put("TypeEquals_Valid_36", "Issue ???");
        IGNORED.put("TypeEquals_Valid_37", "Issue ???");
        IGNORED.put("TypeEquals_Valid_38", "Issue ???");
        IGNORED.put("TypeEquals_Valid_41", "Issue ???");
        IGNORED.put("While_Valid_15", "unknown");

        // Fails and was not listed as test case before parameterizing
        IGNORED.put("DoWhile_Valid_7", "unknown");
        IGNORED.put("Function_Valid_11", "unknown");
        IGNORED.put("Function_Valid_15", "unknown");
        IGNORED.put("While_Valid_48", "unknown");

        // Fails and was not annotated with @Test before parameterizing
        IGNORED.put("While_Valid_7", "unknown");
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return TestUtils.findTestNames("tests/valid/");
    }

    private String testName;

    public ValidTests (String testName) {
        this.testName = testName;
    }

    @Test
    public void test () {
//        try {

        Pair<Integer, String> val = TestUtils.compile("-wd", "tests/valid/","tests/valid/"+testName+".whiley");

//        System.out.println(val);
        if (val.first() != 0) {
            System.err.println(TestUtils.compile("-version"));
            fail(val.toString());
        }
        //WasmFileWriter.main(new String[]{"tests/valid/"+testName+".wyil"});
        try {
            // First, read the input file to generate WyilFile instance
            WyilFile file = new WyilFileReader("tests/valid/"+testName+".wyil").read();
            // Second, pass WyilFile into wasm file writer
            new WasmFileWriter(System.out, new WastFactory.SWastFactory()).write(file);
        } catch (IOException e) {
            e.printStackTrace();
            fail("wyil file missing.");
        }

        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter("wasm/test2.wast", true));
            bw.write("\n( invoke \"test\" )");
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String wasmVal = TestUtils.execWast("../../WebAssembly/spec/ml-proto/", "wasm", "test2.wast");

        if (wasmVal == null) {
            fail("Test failed to interpret");
        } else {
            System.out.println(wasmVal);
        }

//        } catch (IOException e) {
//            e.printStackTrace();
//        }

    }

    // Skip ignored tests
    @Before
    public void beforeMethod() {
        String ignored = IGNORED.get(this.testName);
        Assume.assumeTrue("Test " + this.testName + " skipped: " + ignored, ignored == null);
    }

}
