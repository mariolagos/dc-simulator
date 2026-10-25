package org.supply.domain;

import java.util.*;

/** Deterministic union of electrical nodes, without discarding physical node identities. */
public final class IdealNodeGroups {
    private final Map<String,String> parent = new LinkedHashMap<>();
    public IdealNodeGroups(Collection<String> ids) { for (String id : ids) parent.put(id,id); }
    public String group(String id) {
        if (!parent.containsKey(id)) throw new IllegalArgumentException("Unknown ideal connection node: " + id);
        String p=parent.get(id);
        if (!p.equals(id)) parent.put(id,p=group(p));
        return p;
    }
    public void join(String a,String b) {
        a=group(a);b=group(b);
        if (!a.equals(b)) { if(a.compareTo(b)<0) parent.put(b,a); else parent.put(a,b); }
    }
    public Map<String,String> groups() {
        Map<String,String> result=new LinkedHashMap<>();parent.keySet().forEach(id->result.put(id,group(id)));
        return Collections.unmodifiableMap(result);
    }
}
