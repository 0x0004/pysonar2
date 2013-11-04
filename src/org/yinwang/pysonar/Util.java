package org.yinwang.pysonar;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.*;

public class Util {

    public static final Charset UTF_8 = Charset.forName("UTF-8");

    private static int gensymCount = -1;

    @NotNull
    public static String gensym(String base) {
        gensymCount++;
        return base + gensymCount;
    }

    public static String getSystemTempDir() {
        String tmp = System.getProperty("java.io.tmpdir");
        String sep = System.getProperty("file.separator");
        if (tmp.endsWith(sep)) {
            return tmp;
        }
        return tmp + sep;
    }

    /**
     * Returns the parent qname of {@code qname} -- everything up to the
     * last dot (exclusive), or if there are no dots, the empty string.
     */
    public static String getQnameParent(@Nullable String qname) {
        if (qname == null || qname.isEmpty()) {
            return "";
        }
        int index = qname.lastIndexOf(".");
        if (index == -1) {
            return "";
        }
        return qname.substring(0, index);
    }

    /**
     * Determines the fully-qualified module name for the specified file.  A
     * module's qname is a function of the module's absolute path and the sys
     * path; it does not depend on how the module name may have been specified
     * in import statements. The module qname is the relative path of the module
     * under the load path, with slashes converted to dots.
     *
     * @param file absolute canonical path to a file (__init__.py for dirs)
     * @return null if {@code file} is not somewhere under the load path
     */
    @Nullable
    public static String moduleQname(@NotNull String file) {
        boolean initpy = file.endsWith("/__init__.py");
        if (initpy) {
            file = file.substring(0, file.length() - "/__init__.py".length());
        } else if (file.endsWith(".py")) {
            file = file.substring(0, file.length() - ".py".length());
        }
        for (String root : Indexer.idx.getLoadPath()) {
            if (file.startsWith(root)) {
                return file.substring(root.length()).replace('/', '.');
            }
        }
        return null;
    }

    @NotNull
    public static String arrayToString(@NotNull Collection<String> strings) {
        StringBuffer sb = new StringBuffer();
        for (String s : strings) {
            sb.append(s).append("\n");
        }
        return sb.toString();
    }

    @NotNull
    public static String arrayToSortedStringSet(Collection<String> strings) {
        Set<String> sorter = new TreeSet<String>();
        sorter.addAll(strings);
        return arrayToString(sorter);
    }

    /**
     * Given an absolute {@code path} to a file (not a directory),
     * returns the module name for the file.  If the file is an __init__.py,
     * returns the last component of the file's parent directory, else
     * returns the filename without path or extension.
     */
    public static String moduleNameFor(String path) {
        File f = new File(path);
        if (f.isDirectory()) {
            throw new IllegalStateException("failed assertion: " + path);
        }
        String fname = f.getName();
        if (fname.equals("__init__.py")) {
            return f.getParentFile().getName();
        }
        return fname.substring(0, fname.lastIndexOf('.'));
    }

    @NotNull
    public static File joinPath(@NotNull File dir, String file) {
        return joinPath(dir.getAbsolutePath(), file);
    }

    @NotNull
    public static File joinPath(String dir, String file) {
        File file1 = new File(dir);
        File file2 = new File(file1, file);
        return file2;
    }

    public static void writeFile(String path, String contents) throws Exception {
        PrintWriter out = null;
        try {
            out = new PrintWriter(new BufferedWriter(new FileWriter(path)));
            out.print(contents);
            out.flush();
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    @NotNull
    public static String readFile(String filename) throws Exception {
        return readFile(new File(filename));
    }

    @NotNull
    public static String readFile(@NotNull File path) throws Exception {
        // Don't use line-oriented file read -- need to retain CRLF if present
        // so the style-run and link offsets are correct.
        return new String(getBytesFromFile(path), UTF_8);
    }


    @NotNull
    public static byte[] getBytesFromFile(@NotNull File file) {
        InputStream is = null;

        try {
            is = new FileInputStream(file);
            long length = file.length();
            if (length > Integer.MAX_VALUE) {
                throw new IOException("file too large: " + file);
            }

            byte[] bytes = new byte[(int)length];
            int offset = 0;
            int numRead = 0;
            while (offset < bytes.length
                   && (numRead = is.read(bytes, offset, bytes.length-offset)) >= 0) {
                offset += numRead;
            }
            if (offset < bytes.length) {
                throw new IOException("Failed to read whole file " + file);
            }
            return bytes;
        } catch (Exception e) {
            return null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                }
            }
        }
    }


    @NotNull
    public static String readWhole(@NotNull InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        byte[] bytes = new byte[8192];

        int nRead;
        while ((nRead = is.read(bytes, 0, 8192)) > 0) {
            sb.append(new String(bytes, 0, nRead));
        }
        return sb.toString();
    }


    @NotNull
    public static String getMD5(@NotNull File path) {
        byte[] bytes = getBytesFromFile(path);
        if (bytes == null) {
            Util.msg("getMD5: failed to read from file: " + path);
            System.exit(2);
            return "";
        } else {
            return getMD5(bytes);
        }
    }


    @NotNull
    public static String getMD5(byte[] fileContents) {
        MessageDigest algorithm;

        try {
             algorithm = MessageDigest.getInstance("MD5");
        } catch (Exception e) {
            Util.die("getMD5: failed to get MD5, shouldn't happen");
            return "";
        }

        algorithm.reset();
        algorithm.update(fileContents);
        byte messageDigest[] = algorithm.digest();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messageDigest.length; i++) {
            sb.append(String.format("%02x", 0xFF & messageDigest[i]));
        }
        return sb.toString();
    }

    /**
     * Return absolute path for {@code path}.
     * Make sure path ends with "/" if it's a directory.
     * Does _not_ resolve symlinks, since the caller may need to play
     * symlink tricks to produce the desired paths for loaded modules.
     */
    public static String canonicalize(String path) {
        File f = new File(path);
        path = f.getAbsolutePath();
        if (f.isDirectory() && !path.endsWith("/")) {
            return path + "/";
        } else if (f.isDirectory() && !path.endsWith("\\")) {
            return path + "\\";
        }

        return path;
    }

    static boolean isReadableFile(String path) {
        File f = new File(path);
        return f.canRead() && f.isFile();
    }

    static public String escapeQname_(@NotNull String s) {
        return s.replaceAll("[.&@%-]", "_");
    }

    @NotNull
    public static Collection<String> toStringCollection(@NotNull Collection<Integer> collection) {
        List<String> ret = new ArrayList<String>();
        for (Integer x : collection) {
            ret.add(x.toString());
        }
        return ret;
    }

    @NotNull
    static public String joinWithSep(@NotNull Collection<String> ls, String sep, @Nullable String start, @Nullable String end) {
        StringBuilder sb = new StringBuilder();
        if (start != null && ls.size() > 1) {
            sb.append(start);
        }
        int i = 0;
        for (String s: ls) {
            if (i > 0) {
                sb.append(sep);
            }
            sb.append(s);
            i++;
        }
        if (end != null && ls.size() > 1) {
            sb.append(end);
        }
        return sb.toString();
    }

    @NotNull
    public static String timeString(long millis) {
        long sec = millis / 1000;
        long min = sec / 60;
        sec = sec % 60;
        long hr = min / 60;
        min = min % 60;

        return hr + ":" + min + ":" + sec;
    }


    public static void msg(String m) {
        System.out.println(m);
    }

    @Nullable
    public static String readWholeFile(String filename) {
        try {
            return new Scanner(new File(filename)).useDelimiter("PYSONAR2END").next();
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    public static String readWholeStream(InputStream in) throws Exception {
        return new Scanner(in).useDelimiter("\\n").next();
    }


    @NotNull
    private static String percent(int num, int total) {
        double pct = (num * 1.0) / total;
        pct = Math.round(pct * 10000) / 100.0;
        return num + "/" + total + " = " + pct + "%";
    }


    public static void die(String msg) {
        die(msg, null);
    }

    public static void die(String msg, Exception e) {
        System.err.println(msg);

        if (e != null) {
            System.err.println("Exception: " + e + "\n");
        }

        Thread.dumpStack();
        System.exit(2);
    }

}
