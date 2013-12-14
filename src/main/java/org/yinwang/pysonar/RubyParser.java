package org.yinwang.pysonar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yinwang.pysonar.ast.*;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class RubyParser extends Parser {

    private static final String RUBY_EXE = "irb";
    private static final int TIMEOUT = 10000;

    @Nullable
    Process rubyProcess;
    private static Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final String dumpPythonResource = "org/yinwang/pysonar/ruby/dump_ruby.rb";
    private String exchangeFile;
    private String endMark;
    private String jsonizer;
    private String parserLog;


    public RubyParser() {
        String tmpDir = _.getSystemTempDir();
        String sid = _.newSessionId();

        exchangeFile = _.makePathString(tmpDir, "pysonar2", "json." + sid);
        endMark = _.makePathString(tmpDir, "pysonar2", "end." + sid);
        jsonizer = _.makePathString(tmpDir, "pysonar2", "dump_ruby." + sid);
        parserLog = _.makePathString(tmpDir, "pysonar2", "parser_log." + sid);

        startRubyProcesses();

        if (rubyProcess != null) {
            _.msg("Started: " + RUBY_EXE);
        }
    }


    // start or restart ruby process
    private void startRubyProcesses() {
        if (rubyProcess != null) {
            rubyProcess.destroy();
        }

        rubyProcess = startInterpreter(RUBY_EXE);

        if (rubyProcess == null) {
            _.die("You don't seem to have ruby on PATH");
        }
    }


    public void close() {
        new File(jsonizer).delete();
        new File(exchangeFile).delete();
        new File(endMark).delete();
    }


    @Nullable
    public Node convert(Object o) {
        if (!(o instanceof Map) || ((Map) o).isEmpty()) {
            return null;
        }

        Map<String, Object> map = (Map<String, Object>) o;

        String type = (String) map.get("type");
        Double startDouble = (Double) map.get("start");
        Double endDouble = (Double) map.get("end");

        int start = startDouble == null ? 0 : startDouble.intValue();
        int end = endDouble == null ? 1 : endDouble.intValue();


        if (type.equals("program")) {
            Block b = (Block) convert(map.get("body"));
            String file = (String) map.get("filename");
            if (file != null) {
                b.setFile(_.unifyPath(file));
            } else {
                _.die("program should contain a filename field, please check the parser");
            }

            return b;
        }

        if (type.equals("module")) {
            Block b = (Block) convert(map.get("body"));
            Module m = new Module(b, start, end);
            try {
                m.setFile(_.unifyPath((String) map.get("filename")));
            } catch (Exception e) {

            }
            return m;
        }

        if (type.equals("block")) {
            List<Node> stmts = convertList(map.get("stmts"));
            return new Block(stmts, start, end);
        }

        if (type.equals("def")) {
            Name name = (Name) convert(map.get("name"));
            Block body = (Block) convert(map.get("body"));
            Map<String, Object> argsMap = (Map<String, Object>) map.get("params");
            List<Node> positional = convertList(argsMap.get("positional"));
            List<Node> defaults = convertList(argsMap.get("defaults"));
            Name var = (Name) convert(argsMap.get("rest"));
            Name vararg = var == null ? null : var;
            FunctionDef ret = new FunctionDef(name, positional, body, defaults, vararg, null, start, end);
            ret.afterRest = convertList(argsMap.get("after_rest"));
            ret.blockarg = (Name) convert(argsMap.get("block"));
            return ret;
        }

        if (type.equals("funblock")) {
            Block body = (Block) convert(map.get("body"));
            Map<String, Object> argsMap = (Map<String, Object>) map.get("params");
            List<Node> positional = convertList(argsMap.get("positional"));
            List<Node> defaults = convertList(argsMap.get("defaults"));
            Name var = (Name) convert(argsMap.get("rest"));
            Name vararg = var == null ? null : var;
            FunctionDef ret = new Lambda(positional, body, defaults, vararg, null, start, end);
            ret.afterRest = convertList(argsMap.get("after_rest"));
            ret.blockarg = (Name) convert(argsMap.get("block"));
            return ret;
        }

        if (type.equals("call")) {
            Node func = convert(map.get("func"));
            Map<String, Object> args = (Map<String, Object>) map.get("args");
            List<Node> posKey = convertList(args.get("positional"));
            List<Node> pos = new ArrayList<>();
            List<Keyword> kws = new ArrayList<>();
            for (Node node : posKey) {
                if (node.isAssign()) {
                    kws.add(new Keyword(node.asAssign().target.asName().id,
                            node.asAssign().value,
                            node.start,
                            node.end));
                } else {
                    pos.add(node);
                }

            }

            Call ret = new Call(func, pos, kws, null, null, start, end);

            Node blockarg = convert(map.get("block_arg"));
            if (blockarg != null) {
                ret.blockarg = blockarg;
            }
            return ret;
        }

        if (type.equals("attribute")) {
            Node value = convert(map.get("value"));
            Name attr = (Name) convert(map.get("attr"));
            return new Attribute(value, attr, start, end);
        }

        if (type.equals("binary")) {
            Node left = convert(map.get("left"));
            Node right = convert(map.get("right"));
            Op op = convertOp(map.get("op"));

            // compositional operators
            if (op == Op.NotEqual) {
                Node eq = new BinOp(Op.Equal, left, right, start, end);
                return new UnaryOp(Op.Not, eq, start, end);
            }

            if (op == Op.LtE) {
                Node lt = new BinOp(Op.Lt, left, right, start, end);
                Node eq = new BinOp(Op.Eq, left, right, start, end);
                return new BinOp(Op.Or, lt, eq, start, end);
            }

            if (op == Op.GtE) {
                Node gt = new BinOp(Op.Gt, left, right, start, end);
                Node eq = new BinOp(Op.Eq, left, right, start, end);
                return new BinOp(Op.Or, gt, eq, start, end);
            }

            if (op == Op.NotIn) {
                Node in = new BinOp(Op.In, left, right, start, end);
                return new UnaryOp(Op.Not, in, start, end);
            }

            if (op == Op.NotEq) {
                Node in = new BinOp(Op.Eq, left, right, start, end);
                return new UnaryOp(Op.Not, in, start, end);
            }

            return new BinOp(op, left, right, start, end);

        }


        if (type.equals("void")) {
            return new Pass(start, end);
        }


        if (type.equals("break")) {
            return new Break(start, end);
        }

        if (type.equals("class")) {
            Name name = (Name) convert(map.get("name"));
            Node base = convert(map.get("super"));
            List<Node> bases = new ArrayList<>();
            if (base != null) {
                bases.add(base);
            }
            Block body = (Block) convert(map.get("body"));
            return new ClassDef(name, bases, body, start, end);
        }

        if (type.equals("continue")) {
            return new Continue(start, end);
        }

        if (type.equals("undef")) {
            List<Node> targets = convertList(map.get("names"));
            return new Delete(targets, start, end);
        }

        if (type.equals("hash")) {
            List<Map<String, Object>> entries = (List<Map<String, Object>>) map.get("entries");
            List<Node> keys = new ArrayList<>();
            List<Node> values = new ArrayList<>();
            for (Map<String, Object> e : entries) {
                keys.add(convert(e.get("key")));
                values.add(convert(e.get("value")));
            }
            return new Dict(keys, values, start, end);
        }

        if (type.equals("rescue")) {
            List<Node> exceptions = convertList(map.get("exceptions"));
            Node binder = convert(map.get("binder"));
            Block body = (Block) convert(map.get("body"));
            return new Handler(exceptions, binder, body, start, end);
        }

        if (type.equals("for")) {
            Node target = convert(map.get("target"));
            Node iter = convert(map.get("iter"));
            Block body = (Block) convert(map.get("body"));
            return new For(target, iter, body, null, start, end);
        }

        if (type.equals("if")) {
            Node test = convert(map.get("test"));
            Block body = (Block) convert(map.get("body"));
            Block orelse = (Block) convert(map.get("else"));
            return new If(test, body, orelse, start, end);
        }

        if (type.equals("keyword")) {
            String arg = (String) map.get("arg");
            Node value = convert(map.get("value"));
            return new Keyword(arg, value, start, end);
        }

        if (type.equals("array")) {
            List<Node> elts = convertList(map.get("elts"));
            return new NList(elts, start, end);
        }

        if (type.equals("star")) { // f(*[1, 2, 3, 4])
            Node value = convert(map.get("value"));
            return new Starred(value, start, end);
        }

        // another name for Name in Python3 func parameters?
        if (type.equals("arg")) {
            String id = (String) map.get("arg");
            return new Name(id, start, end);
        }

        if (type.equals("return")) {
            Node value = convert(map.get("value"));
            return new Return(value, start, end);
        }

        if (type.equals("string")) {
            String s = (String) map.get("value");
            return new Str(s, start, end);
        }

        // Ruby's subscript is Python's Slice with step size 1
        if (type.equals("subscript")) {
            Node value = convert(map.get("value"));
            List<Node> s = convertList(map.get("slice"));
            if (s.size() == 1) {
                Node node = s.get(0);
                Index idx = new Index(node, node.start, node.end);
                return new Subscript(value, idx, start, end);
            } else if (s.size() == 2) {
                Slice slice = new Slice(s.get(0), null, s.get(1), s.get(0).start, s.get(1).end);
                return new Subscript(value, slice, start, end);
            } else {
                _.die("illegal format of subscript, please fix parser");
            }
        }

        if (type.equals("begin")) {
            Block body = (Block) convert(map.get("body"));
            Block orelse = (Block) convert(map.get("else"));
            Block finalbody = (Block) convert(map.get("ensure"));
            return new Try(null, body, orelse, finalbody, start, end);
        }

        if (type.equals("unary")) {
            Op op = convertOp(map.get("op"));
            Node operand = convert(map.get("operand"));
            return new UnaryOp(op, operand, start, end);
        }

        if (type.equals("while")) {
            Node test = convert(map.get("test"));
            Block body = (Block) convert(map.get("body"));
            return new While(test, body, null, start, end);
        }

        if (type.equals("yield")) {
            Node value = convert(map.get("value"));
            return new Yield(value, start, end);
        }

        if (type.equals("assign")) {
            Node target = convert(map.get("target"));
            Node value = convert(map.get("value"));
            return new Assign(target, value, start, end);
        }

        if (type.equals("name")) {
            String id = (String) map.get("id");
            return new Name(id, start, end);
        }

        if (type.equals("symbol")) {
            String id = (String) map.get("id");
            return new Name(id, start, end);
        }

        if (type.equals("int") || type.equals("float")) {
            Object n = map.get("value");
            return new Num(n, start, end);
        }

        _.die("[please report parser bug]: unexpected ast node: " + type);

        return null;
    }


    @Nullable
    private <T> List<T> convertList(@Nullable Object o) {
        if (o == null) {
            return null;
        } else {
            List<Map<String, Object>> in = (List<Map<String, Object>>) o;
            List<T> out = new ArrayList<>();

            for (Map<String, Object> m : in) {
                Node n = convert(m);
                if (n != null) {
                    out.add((T) n);
                }
            }

            return out;
        }
    }


    public Op convertOp(Object map) {
        String name = (String) ((Map<String, Object>) map).get("name");

        if (name.equals("+")) {
            return Op.Add;
        }

        if (name.equals("-")) {
            return Op.Sub;
        }

        if (name.equals("*")) {
            return Op.Mul;
        }

        if (name.equals("/")) {
            return Op.Div;
        }

        if (name.equals("**")) {
            return Op.Pow;
        }

        if (name.equals("==")) {
            return Op.Equal;
        }

        if (name.equals("Is")) {
            return Op.Eq;
        }

        if (name.equals("<")) {
            return Op.Lt;
        }

        if (name.equals(">")) {
            return Op.Gt;
        }


        if (name.equals("&")) {
            return Op.BitAnd;
        }

        if (name.equals("|")) {
            return Op.BitOr;
        }

        if (name.equals("^")) {
            return Op.BitXor;
        }


        if (name.equals("In")) {
            return Op.In;
        }


        if (name.equals("<<")) {
            return Op.LShift;
        }

        if (name.equals("FloorDiv")) {
            return Op.FloorDiv;
        }

        if (name.equals("%")) {
            return Op.Mod;
        }

        if (name.equals(">>")) {
            return Op.RShift;
        }

        if (name.equals("~")) {
            return Op.Invert;
        }

        if (name.equals("and")) {
            return Op.And;
        }

        if (name.equals("or")) {
            return Op.Or;
        }

        if (name.equals("not")) {
            return Op.Not;
        }

        if (name.equals("!=")) {
            return Op.NotEqual;
        }

        if (name.equals("IsNot")) {
            return Op.NotEq;
        }

        if (name.equals("<=")) {
            return Op.LtE;
        }

        if (name.equals(">=")) {
            return Op.GtE;
        }

        if (name.equals("NotIn")) {
            return Op.NotIn;
        }

        _.die("illegal operator: " + name);
        return null;
    }


    public String prettyJson(String json) {
        Map<String, Object> obj = gson.fromJson(json, Map.class);
        return gson.toJson(obj);
    }


    @Nullable
    public Process startInterpreter(String interpExe) {
        String jsonizeStr;
        Process p;

        try {
            InputStream jsonize = Thread.currentThread().getContextClassLoader().getResourceAsStream(
                    dumpPythonResource);
            jsonizeStr = _.readWholeStream(jsonize);
        } catch (Exception e) {
            _.die("Failed to open resource file:" + dumpPythonResource);
            return null;
        }

        try {
            FileWriter fw = new FileWriter(jsonizer);
            fw.write(jsonizeStr);
            fw.close();
        } catch (Exception e) {
            _.die("Failed to write into: " + jsonizer);
            return null;
        }

        try {
            ProcessBuilder builder = new ProcessBuilder(interpExe);
            builder.redirectErrorStream(true);
            builder.redirectError(new File(parserLog));
            builder.redirectOutput(new File(parserLog));
            builder.environment().remove("PYTHONPATH");
            p = builder.start();
        } catch (Exception e) {
            _.die("Failed to start irb");
            return null;
        }

        if (!sendCommand("load '" + jsonizer + "'", p)) {
            _.die("Failed to load jsonizer, please report bug");
            p.destroy();
            return null;
        }

        return p;
    }


    @Nullable
    public Node parseFile(String filename) {
        Node node = parseFileInner(filename, rubyProcess);
        if (node != null) {
            return node;
        } else {
            Analyzer.self.failedToParse.add(filename);
            return null;
        }
    }


    @Nullable
    public Node parseFileInner(String filename, @NotNull Process rubyProcess) {
//        Util.msg("parsing: " + filename + " using " + pythonProcess);

        File exchange = new File(exchangeFile);
        File marker = new File(endMark);
        cleanTemp();

        String s1 = _.escapeWindowsPath(filename);
        String s2 = _.escapeWindowsPath(exchangeFile);
        String s3 = _.escapeWindowsPath(endMark);
        String dumpCommand = "parse_dump('" + s1 + "', '" + s2 + "', '" + s3 + "')";

        if (!sendCommand(dumpCommand, rubyProcess)) {
            cleanTemp();
            return null;
        }

        long waitStart = System.currentTimeMillis();
        while (!marker.exists()) {
            if (System.currentTimeMillis() - waitStart > TIMEOUT) {
                _.msg("\nTimed out while parsing: " + filename);
                cleanTemp();
                startRubyProcesses();
                return null;
            }

            try {
                Thread.sleep(1);
            } catch (Exception e) {
                cleanTemp();
                return null;
            }
        }

        String json;
        try {
            json = _.readFile(exchangeFile);
        } catch (Exception e) {
            cleanTemp();
            return null;
        }

        cleanTemp();

        Map<String, Object> map = gson.fromJson(json, Map.class);
        return convert(map);
    }


    private boolean sendCommand(String cmd, @NotNull Process rubyProcess) {
        _.msg("sending cmd: " + cmd);
        try {
            OutputStreamWriter writer = new OutputStreamWriter(rubyProcess.getOutputStream());
            writer.write(cmd);
            writer.write("\n");
            writer.flush();
            return true;
        } catch (Exception e) {
            _.msg("\nFailed to send command to Ruby interpreter: " + cmd);
            return false;
        }
    }


    private void cleanTemp() {
        new File(exchangeFile).delete();
        new File(endMark).delete();
    }


    public static void main(String[] args) {
        RubyParser parser = new RubyParser();
        parser.parseFile(args[0]);
    }

}
