package com.ibm.wala.ipa.callgraph.multithread.statements;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.multithread.analyses.HeapAbstractionFactory;
import com.ibm.wala.ipa.callgraph.multithread.engine.PointsToAnalysis.StmtAndContext;
import com.ibm.wala.ipa.callgraph.multithread.graph.GraphDelta;
import com.ibm.wala.ipa.callgraph.multithread.graph.PointsToGraph;
import com.ibm.wala.ipa.callgraph.multithread.graph.PointsToGraphNode;
import com.ibm.wala.ipa.callgraph.multithread.graph.ReferenceVariableReplica;
import com.ibm.wala.ipa.callgraph.multithread.graph.TypeFilter;
import com.ibm.wala.ipa.callgraph.multithread.registrar.StatementRegistrar;
import com.ibm.wala.ipa.callgraph.multithread.registrar.ReferenceVariableFactory.ReferenceVariable;
import com.ibm.wala.ipa.callgraph.multithread.util.print.PrettyPrinter;

/**
 * Points-to statement for a local assignment, left = right
 */
public class LocalToLocalStatement extends PointsToStatement {

    /**
     * assignee
     */
    private final ReferenceVariable left;
    /**
     * assigned
     */
    private ReferenceVariable right;

    private final boolean filter;

    /**
     * Is right from a method summary?
     */
    private final boolean isFromMethodSummaryVariable;

    /**
     * Statement for a local assignment, left = right
     *
     * @param left
     *            points-to graph node for assignee
     * @param right
     *            points-to graph node for the assigned value
     * @param m
     *            method the assignment is from
     */
    protected LocalToLocalStatement(ReferenceVariable left,
            ReferenceVariable right, IMethod m, boolean filterBasedOnType,
            boolean isFromMethodSummaryVariable) {
        super(m);
        assert !left.isSingleton() : left + " is static";
        this.left = left;
        this.right = right;
        filter = filterBasedOnType;
        this.isFromMethodSummaryVariable = isFromMethodSummaryVariable;
    }

    @Override
    public GraphDelta process(Context context, HeapAbstractionFactory haf,
            PointsToGraph g, GraphDelta delta, StatementRegistrar registrar, StmtAndContext originator) {
        PointsToGraphNode l = new ReferenceVariableReplica(context, left, haf);
        PointsToGraphNode r = new ReferenceVariableReplica(context, right, haf);
        // don't need to use delta, as this just adds a subset edge
        if (filter) {
            TypeFilter typeFilter = TypeFilter.create(left.getExpectedType());
            return g.copyFilteredEdges(r, typeFilter, l);
        }
        return g.copyEdges(r, l);
    }

    @Override
    public String toString() {
        return left + " = (" + PrettyPrinter.typeString(left.getExpectedType())
                + ") " + right;
    }

    @Override
    public void replaceUse(int useNumber, ReferenceVariable newVariable) {
        assert useNumber == 0;
        right = newVariable;
    }

    @Override
    public List<ReferenceVariable> getUses() {
        return Collections.singletonList(right);
    }

    @Override
    public ReferenceVariable getDef() {
        return left;
    }

}
