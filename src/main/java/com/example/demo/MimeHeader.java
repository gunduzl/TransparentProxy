package com.example.demo;

import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.Iterator;

public class MimeHeader extends HashMap<String, String> {
    public MimeHeader(String s) {
        parse(s);
    }

    public void parse(String s) {
        StringTokenizer st = new StringTokenizer(s, "\r\n");
        while (st.hasMoreTokens()) {
            String line = st.nextToken();
            int colon = line.indexOf(':');
            String key = line.substring(0, colon);
            String value = line.substring(colon + 2);
            put(key, value);
        }
    }

    @Override
    public String toString() {
        String str = "";
        Iterator<String> it = keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            str += key + ": " + get(key) + "\r\n";
        }

        return str;

    }
}