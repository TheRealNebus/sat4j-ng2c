package vmalloc.algorithm;

import org.sat4j.core.Vec;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.IVec;
import org.sat4j.specs.IVecInt;

import vmalloc.constraints.ConstraintID;
import vmalloc.constraints.ConstraintSolver;
import vmalloc.evolutionary.VMCwMProblem;

public class ParetoCLD extends MultiObjectiveConstraintBasedAllocAlgorithm {

    public ParetoCLD(VMCwMProblem instance) { this(instance, false); }
    public ParetoCLD(VMCwMProblem instance, boolean break_symms) {
        super(instance, break_symms);
    }

    @Override
    public void allocate() {
        System.out.println("c Initializing");
        ConstraintSolver solver = null;
        try {
            solver = buildSolver();
        }
        catch (ContradictionException e) {
            printUnsatisfiable();
            return;
        }
        System.out.println("c Initializing objective functions");
        initializeObjectiveFunctions();
        printElapsedTime();
        IVecInt undef_fmls = buildUndefFormulas();
        boolean mcs_exists = false, next_mcs = false;
        IVec<ConstraintID> to_remove = new Vec<ConstraintID>();
        while (true) {
            System.out.println("c Computing mapping");
            checkSAT(solver);
            if (!solver.isSolved()) {
                printTimeoutMessage();
                return;
            }
            else if (solver.isSatisfiable()) {
                mcs_exists = true;
                saveSolution(modelToAllocation(solver,
                                               this.instance.getPhysicalMachines(),
                                               this.instance.getJobs(),
                                               this.job_vars),
                             true);
                IVecInt asms = extractSatisfied(solver, undef_fmls);
                try {
                    addRemovableConjunction(solver, asms, to_remove);
                    to_remove.push(solver.addRemovableClause(undef_fmls));
                }
                catch (ContradictionException e) {
                    next_mcs = true;
                }
            }
            else {
                if (!mcs_exists) {
                    printOptimum();
                    return;
                }
                else {
                    next_mcs = true;
                }
            }
            if (mcs_exists && next_mcs) {
                System.out.println("c MCS computed, generating another one");
                mcs_exists = next_mcs = false;
                solver.removeConstraints(to_remove);
                to_remove.clear();
                try {
                    solver.addClause(undef_fmls); // block MCS
                    undef_fmls = buildUndefFormulas();
                }
                catch (ContradictionException e) {
                    printOptimum();
                    return;
                }
            }
        }
    }

}
