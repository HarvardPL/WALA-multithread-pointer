package com.ibm.wala.ipa.callgraph.multithread.analyses;

import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.multithread.statements.CallSiteLabel;
import com.ibm.wala.ipa.callgraph.multithread.statements.AllocSiteNodeFactory.AllocSiteNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;

/**
 * Analysis that tracks call sites of (only) static methods
 */
public class StaticCallSiteSensitive extends HeapAbstractionFactory {

    /**
     * Default depth of call sites to keep track of
     */
    private static final int DEFAULT_SENSITIVITY = 2;
    private final int sensitivity;

    /**
     * Create a static call site sensitive heap abstraction factory with the default depth, i.e. up to the default
     * number of static call sites are tracked
     */
    public StaticCallSiteSensitive() {
        this(DEFAULT_SENSITIVITY);
    }

    /**
     * Create a static call site sensitive heap abstraction factory with the given depth, i.e. up to depth static call
     * sites are tracked
     * 
     * @param sensitivity
     *            depth of the call site stack
     */
    public StaticCallSiteSensitive(int sensitivity) {
        this.sensitivity = sensitivity;
    }
    

    public int getSensitivity() {
        return sensitivity;
    }

    @SuppressWarnings("unchecked")
    @Override
    public AllocationName<ContextStack<CallSiteLabel>> record(AllocSiteNode allocationSite, Context context) {
        return AllocationName.create((ContextStack<CallSiteLabel>) context, allocationSite);
    }

    @SuppressWarnings("unchecked")
    @Override
    public ContextStack<CallSiteLabel> merge(CallSiteLabel callSite, InstanceKey receiver, Context callerContext) {
        if (!callSite.isStatic()) {
            // only track call sites to static methods.
            callSite = null;
        }
        return ((ContextStack<CallSiteLabel>) callerContext).push(callSite, sensitivity);
    }

    @Override
    public ContextStack<CallSiteLabel> initialContext() {
        return ContextStack.<CallSiteLabel> emptyStack();
    }

    @Override
    public String toString() {
        return "scs(" + this.getSensitivity() + ")";
    }
}
