package droidsafe.analyses.rcfg;

import java.util.Formatter;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.DynamicInvokeExpr;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.spark.pag.AllocNode;
import soot.jimple.spark.pag.StringConstantNode;
import soot.jimple.toolkits.callgraph.Edge;
import droidsafe.analyses.GeoPTA;
import droidsafe.analyses.PTAMethodInformation;
import droidsafe.utils.SourceLocationTag;
import droidsafe.utils.Utils;

/**
 * This class represents an API call in the rCFG.  It is associated with an rCFG node (an input event).  
 * Each output event currently has an underlying soot method, a context edge, a calling edge, and possibly a 
 * receiver.  
 * 
 * @author mgordon
 *
 */
public class OutputEvent implements PTAMethodInformation {
    /** logger field */
    private static final Logger logger = LoggerFactory.getLogger(OutputEvent.class);
    /** the cg edge from caller to API */
    private Edge thisEdge;
    /** the cg context edge from previous caller to caller */
    private Edge contextEdge;
    /** The invoke expression call to an API method, might be null EX finalize*/
    private InvokeExpr invokeExpr;
    /** The parent RFCG Node */
    private RCFGNode parent;
    /** Source locations of the calls for this output event for the given input event */
    private SourceLocationTag linesTag = null;
    /** The specific receiver node that triggers this output event */
    private AllocNode receiverNode;

    /**
     * Create an output event from an edge, context edge, ... 
     */
    public OutputEvent(Edge theEdge, Edge ce, RCFGNode p, 
                       AllocNode rn, SourceLocationTag ln) {
        this.thisEdge = theEdge;
        this.contextEdge = ce;
        this.parent = p;
        this.receiverNode = rn;
        this.linesTag = ln;

        setInvoke();
    }

    /**
     * Try to grab the invoke expression from the context
     */
    private void setInvoke() {
        Unit context = thisEdge.srcUnit();
        invokeExpr = null;

        if (context == null) {
            return;
        }

        for (Object vb : context.getUseAndDefBoxes()) {
            Value value = ((ValueBox)vb).getValue();

            if (value instanceof InvokeExpr) {
                InvokeExpr ie = (InvokeExpr)value;
                logger.debug("Found invoke in output event {} matches {}?", ie, this.getTarget());
                //old check that does not work for threads since start calls run...
                //if (Hierarchy.v().canResolveTo(ie.getMethodRef(), this.getTarget())) 
                if (invokeExpr != null) {
                    logger.error("Found multiple matches for calling context in context statement {}.",
                        context);
                    droidsafe.main.Main.exit(1);
                }
                invokeExpr = ie;
            }
        }

        if (invokeExpr == null) {
            logger.error("Cannot find context invoke expr in context: {}.", context);
            droidsafe.main.Main.exit(1);
        }
        if (invokeExpr instanceof SpecialInvokeExpr && !hasReceiver()) {
            logger.info("Found special invoke expr without a receiver {}", invokeExpr);
        }

        //ever instance invoke should have a receiver, unless something wrong with user code or with modeling or with
        //PTA
        if (hasReceiver() != (invokeExpr instanceof InstanceInvokeExpr)) {
            logger.info("Presence of receiver is wrong for invoke expr type: {} and {} receiver (line {}).", 
                invokeExpr, hasReceiver(), linesTag);
        }

        //do some checks for things we might not fully understand yet.
        if (invokeExpr instanceof DynamicInvokeExpr) {
            logger.error("Do not understand type of invoke expr: {}", invokeExpr.getClass());
            droidsafe.main.Main.exit(1);
        }
    }

    /** 
     * Return the number of args in the invoke expression.
     */
    public int getNumArgs() {
        return invokeExpr.getArgCount();
    }

    /**
     * Return true if the method call has a receiver.
     * @return
     */
    public boolean hasReceiver() {
        return receiverNode != null; 
    }


    /**
     * Return the value of the receiver, probably a field or local variable.
     */
    public Value getReceiver() {
        if (!hasReceiver()) {
            logger.error("Trying to get receiver for output event without one: {}.", this.toString());
            droidsafe.main.Main.exit(1);
        }

        return ((InstanceInvokeExpr)invokeExpr).getBase();
    }

    /**
     * Return the edge that called this input event's method.
     */
    public Edge getThisEdge() {
        return thisEdge;
    }

    /**
     * Set the edge of this output event (from usr code to api call)
     */
    public void setThisEdge(Edge e) {
        this.thisEdge = e;
    }

    /**
     * Return the points to set of the receiver (if it exists) in the context of this
     * output event.
     */
    public Set<AllocNode> getReceiverPTSet() {
        getReceiver();

        LinkedHashSet<AllocNode> node = new LinkedHashSet<AllocNode>();
        node.add(receiverNode);
        return node; 
    }

    /**
     * Return true if the receiver is a pointer.
     */
    public boolean isReceiverPointer() {
        return GeoPTA.v().isPointer(getReceiver());
    }
    
    /**
     * Return the type in the points to set of the receiver.
     */
    public Type getReceiverType() {
        return receiverNode.getType();
    }

    /**
     * Return the type of the value for the argument, does not look 
     * at the points to set.
     */
    public Type getArgumentType(int i) {
        return getArgValue(i).getType();
    }

    /**
     * Return the points to set for the pointer argument at index i.
     */
    public Set<AllocNode> getArgPTSet(int i) {
        Value v = getArgValue(i);
        return GeoPTA.v().getPTSet(v, contextEdge);
    }

    /**
     * Return the context edge.
     */
    public Edge getContextEdge() {
        return contextEdge;
    }

    /**
     * Set the context edge.
     */
    public void setContextEdge(Edge ce) {
        this.contextEdge = ce;
    }

    /**
     * Get the target soot method of this output event.
     * @return
     */
    public SootMethod getTarget() {
        return thisEdge.tgt();
    }

    /**
     * Return the argument expression for the call to the API method.
     */
    public Value getArgValue(int i) {
        if (i > getNumArgs()) { 
            logger.error("Trying to invalid argument {} for output event {}.", i, getTarget());
            droidsafe.main.Main.exit(1);
        }

        return invokeExpr.getArg(i);
    }

    /**
     * Return true if arg i value is a pointer in the PT graph.
     */
    public boolean isArgPointer(int i) {
        return GeoPTA.v().isPointer(getArgValue(i));
    }
    
    /**
     * Return the enclosing RCFGNode.
     */
    public RCFGNode getParent() {
        return parent;
    }

    /** 
     * Get the source location tag for this output event.
     */
    public SourceLocationTag getSourceLocationTag() {
        return linesTag;
    }

    /**
     * Return a string representation of the OutputEvent.
     */
    public String toString() {
        StringBuilder str = new StringBuilder();
        Formatter formatter = new Formatter(str, Locale.US);
        str.append(getTarget() + "\n");

        if (hasReceiver()) {
            formatter.format("\tReceiver: %s (%s)\n", getReceiver(), getReceiver().getClass());
            str.append("\t\tPT Set:\n");

            formatter.format("\t\tNode: %s (%s)\n", receiverNode, receiverNode.getClass());

            for (int i = 0; i < getNumArgs(); i++) {
                if (isArgPointer(i)) {
                    Set<AllocNode> nodes = getArgPTSet(i);
                    formatter.format("\tArg %d (size %d)\n", i, nodes.size());
                    for (AllocNode node : nodes) {
                        if (node instanceof StringConstantNode) {
                            formatter.format("\t\tString Constant: %s %s\n", node, 
                                ((StringConstantNode)node).getString());
                        } else {
                            formatter.format("\t\tNode: %s (%s), New expr: %s (%s)\n", 
                                node, node.getClass(), node.getNewExpr(), 
                                node.getNewExpr().getClass());
                        }
                    }
                } else {
                    formatter.format("\tArg %d: %s (%s)\n", i, getArgValue(i).getClass(), getArgValue(i));
                }
            }
        }
        formatter.close();
        return str.toString();
    }
}
