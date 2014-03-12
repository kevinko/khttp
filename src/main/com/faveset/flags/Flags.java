// Copyright 2014, Kevin Ko <kevin@faveset.com>

package com.faveset.flags;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Flags {
    // The singleton flags object.
    private static Flags sFlags;

    private ArrayList<String> mNonFlagArgs;

    // Keyed by flag name.
    private Map<String, FlagParser> mFlagParsers;

    private Flags() {
        mNonFlagArgs = new ArrayList<String>();
        mFlagParsers = new HashMap<String, FlagParser>();
    }

    /**
     * Returns the singleton Flags object.
     */
    public static Flags get() {
        if (sFlags == null) {
            sFlags = new Flags();
        }
        return sFlags;
    }

    /**
     * @return the non-flag argument of given index.
     */
    public String getArg(int index) {
        return mNonFlagArgs.get(index);
    }

    /**
     * @return the number of non-flag arguments.
     */
    public int getArgSize() {
        return mNonFlagArgs.size();
    }

    /**
     * @return an array holding all non-flag arguments.
     */
    public String[] getArgs() {
        return mNonFlagArgs.toArray(new String[0]);
    }

    /**
     * @return true if s is identified as a flag with a leading "-" or "--".
     */
    private static boolean isFlag(String s) {
        return (s.startsWith("-") || s.startsWith("--"));
    }

    /**
     * Call this method to parse the flags in args.
     *
     * @throws IllegalArgumentException if an unknown flag is encountered.
     */
    public static void parse(String[] args) throws IllegalArgumentException {
        get().parseImpl(args);
    }

    /**
     * Call this method to parse the flags in args.
     *
     * @throws IllegalArgumentException if an unknown flag is encountered.
     */
    private void parseImpl(String[] args) throws IllegalArgumentException {
        final int len = args.length;
        for (int ii = 0; ii < len; ii++) {
            String arg = args[ii];
            if (!isFlag(arg)) {
                mNonFlagArgs.add(arg);
                continue;
            }

            // Determine the flag name.
            String flagName;
            String flagValue;

            int equalsIndex = arg.indexOf("=");
            if (equalsIndex == -1) {
                flagName = arg.substring(1);
                flagValue = new String();
            } else {
                flagName = arg.substring(1, equalsIndex);
                flagValue = arg.substring(equalsIndex + 1);
            }

            FlagParser parser = mFlagParsers.get(flagName);
            if (parser == null) {
                throw new IllegalArgumentException(String.format("unknown flag %s", flagName));
            }

            // Now, finalize the value if it is not embedded within the
            // flag argument.  Non-singular flags can use the following
            // argument as the value.
            if (equalsIndex == -1 && !parser.isSingular()) {
                // Check the next argument for the value.
                int nextInd = ii + 1;
                if (nextInd < len) {
                    String nextArg = args[nextInd];
                    if (!isFlag(nextArg)) {
                        // We found the value.
                        flagValue = nextArg;
                    }
                }
            }

            parser.parse(flagValue);
        }
    }

    private void registerFlag(String name, FlagParser flag) {
        mFlagParsers.put(name, flag);
    }

    public static BoolFlag registerBool(String name, boolean defValue, String desc) {
        BoolFlag flag = new BoolFlag(defValue, desc);
        get().registerFlag(name, flag);
        return flag;
    }

    /**
     * Writes a help message for the configured flags using builder.
     */
    public void writeHelp(StringBuilder builder) {
        Set<String> keys = mFlagParsers.keySet();
        List<String> keyList = new ArrayList<String>(keys);
        java.util.Collections.sort(keyList);

        boolean isFirst = true;
        for (String key : keyList) {
            if (isFirst) {
                isFirst = false;
            } else {
                builder.append('\n');
            }

            FlagParser p = mFlagParsers.get(key);
            builder.append(String.format("  -%s=%s: %s",
                        key, p.getDefaultValueString(), p.getDesc()));
        }
    }
}
