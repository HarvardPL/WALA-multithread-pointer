package com.ibm.wala.ipa.callgraph.multithread.statements;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.multithread.analyses.HeapAbstractionFactory;
import com.ibm.wala.ipa.callgraph.multithread.engine.PointsToAnalysis;
import com.ibm.wala.ipa.callgraph.multithread.engine.PointsToAnalysis.StmtAndContext;
import com.ibm.wala.ipa.callgraph.multithread.graph.GraphDelta;
import com.ibm.wala.ipa.callgraph.multithread.graph.PointsToGraph;
import com.ibm.wala.ipa.callgraph.multithread.graph.ReferenceVariableReplica;
import com.ibm.wala.ipa.callgraph.multithread.registrar.ReferenceVariableFactory;
import com.ibm.wala.ipa.callgraph.multithread.registrar.ReferenceVariableFactory.ReferenceVariable;
import com.ibm.wala.ipa.callgraph.multithread.registrar.StatementRegistrar;
import com.ibm.wala.ipa.callgraph.multithread.util.MultiThreadAnalysisUtil;
import com.ibm.wala.ipa.callgraph.multithread.util.OrderedPair;
import com.ibm.wala.ipa.callgraph.multithread.util.print.PrettyPrinter;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

/**
 * Points-to statement for a call to a virtual method (either a class or interface method).
 */
public class VirtualCallStatement extends CallStatement {

    /**
     * Called method
     */
    private final MethodReference callee;
    /**
     * Reference variable for the receiver of the call
     */
    private ReferenceVariable receiver;
    /**
     * Factory used to create callee summary nodes
     */
    private final ReferenceVariableFactory rvFactory;

    /**
     * Points-to statement for a virtual method invocation.
     *
     * @param callSite Method call site
     * @param caller caller method
     * @param callee Method being called
     * @param result Node for the assignee if any (i.e. v in v = foo()), null if there is none or if it is a primitive
     * @param receiver Receiver of the call
     * @param actuals Actual arguments to the call
     * @param exception Node representing the exception thrown by this call (if any)
     * @param rvFactory factory for managing the creation of reference variables for local variables and static fields
     */
    protected VirtualCallStatement(CallSiteReference callSite, IMethod caller, MethodReference callee,
                                   ReferenceVariable result, ReferenceVariable receiver,
                                   List<ReferenceVariable> actuals, ReferenceVariable exception,
                                   ReferenceVariableFactory rvFactory) {
        super(callSite, caller, result, actuals, exception);

        this.callee = callee;
        this.receiver = receiver;
        this.rvFactory = rvFactory;
    }

    @Override
    public GraphDelta process(Context context, HeapAbstractionFactory haf, PointsToGraph g, GraphDelta delta,
                              StatementRegistrar registrar, StmtAndContext originator) {
        ReferenceVariableReplica receiverRep = new ReferenceVariableReplica(context, this.receiver, haf);

        GraphDelta changed = new GraphDelta(g);

        Iterator<InstanceKey> iter = delta == null ? g.pointsToIterator(receiverRep, originator)
                : delta.pointsToIterator(receiverRep);

        while (iter.hasNext()) {
            InstanceKey recHeapContext = iter.next();

            // find the callee.
            // The receiver is recHeapContext, and we want to find a method that matches selector
            // callee.getSelector() in class recHeapContext.getConcreteType() or
            // a superclass.
            IMethod resolvedCallee = this.resolveMethod(recHeapContext.getConcreteType(), receiverRep.getExpectedType());

            if (resolvedCallee != null && resolvedCallee.isAbstract()) {
                // Abstract method due to a native method that returns an abstract type or interface
                // TODO Handle abstract methods in a smarter way
                System.err.println("Abstract method " + PrettyPrinter.methodString(resolvedCallee));
                continue;
            }

            // If we wanted to be very robust, check to make sure that
            // resolvedCallee overrides
            // the IMethod returned by ch.resolveMethod(callee).
            changed = changed.combine(this.processCall(context,
                                                       recHeapContext,
                                                       resolvedCallee,
                                                       g,
                                                       haf,
                                                       registrar.findOrCreateMethodSummary(resolvedCallee,
                                                                                           this.rvFactory)));
        }
        return changed;
    }

    protected IMethod resolveMethod(IClass receiverConcreteType, TypeReference receiverExpectedType) {
        IClassHierarchy cha = MultiThreadAnalysisUtil.getClassHierarchy();
        // XXXX possible point of contention...!@!
        synchronized (cha) {
            IMethod resolvedCallee = cha.resolveMethod(receiverConcreteType, this.callee.getSelector());
            if (resolvedCallee == null) {
                // XXX Try the type of the reference variable instead
                // This is probably a variable created for the return of a native method, then cast down
                if (PointsToAnalysis.outputLevel >= 1) {
                    System.err.println("Could not resolve " + receiverConcreteType + " " + this.callee.getSelector());
                    System.err.println("\ttrying reference variable type " + cha.lookupClass(receiverExpectedType));
                }
                resolvedCallee = cha.resolveMethod(cha.lookupClass(receiverExpectedType), this.callee.getSelector());
            }
            return resolvedCallee;
        }
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        if (this.getResult() != null) {
            s.append(this.getResult().toString() + " = ");
        }
        s.append("invokevirtual " + PrettyPrinter.methodString(this.callee));

        s.append(" -- ");
        s.append(this.receiver);
        s.append(".");
        s.append(this.callee.getName());
        s.append("(");
        List<ReferenceVariable> actuals = this.getActuals();
        if (this.getActuals().size() > 1) {
            s.append(actuals.get(1));
        }
        for (int j = 2; j < actuals.size(); j++) {
            s.append(", ");
            s.append(actuals.get(j));
        }
        s.append(")");

        return s.toString();
    }

    @Override
    public void replaceUse(int useNumber, ReferenceVariable newVariable) {
        assert useNumber <= this.getActuals().size() && useNumber >= 0;
        if (useNumber == 0) {
            this.receiver = newVariable;
            return;
        }
        this.replaceActual(useNumber - 1, newVariable);
    }

    /**
     * Variable for receiver followed by variables for arguments in order
     *
     * {@inheritDoc}
     */
    @Override
    public List<ReferenceVariable> getUses() {
        List<ReferenceVariable> uses = new ArrayList<>(this.getActuals().size() + 1);
        uses.add(this.receiver);
        uses.addAll(this.getActuals());
        return uses;
    }

    @Override
    public ReferenceVariable getDef() {
        return this.getResult();
    }


}
