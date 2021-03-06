package vmalloc.algorithm;

import static org.sat4j.GlobalDefs.USE_NG2C;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.sat4j.core.Vec;
import org.sat4j.core.VecInt;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.IVec;
import org.sat4j.specs.IVecInt;

import vmalloc.constraints.ConstraintID;
import vmalloc.constraints.ConstraintSolver;
import vmalloc.evolutionary.VMCwMProblem;

public class GIAAlloc extends MultiObjectiveConstraintBasedAllocAlgorithm {

    public GIAAlloc(VMCwMProblem instance) { this(instance, false); }
    public GIAAlloc(VMCwMProblem instance, boolean break_symms) {
        super(instance, break_symms);
        setHashFunctionType(HashFunctionType.NONE);
    }

    @Override
    public void allocate() {
        System.out.println("c Initializing");
        if (USE_NG2C) {
            System.newAllocGen();
        }
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
        int enum_threshold = getEnumerationThreshold();
        IVec<ConstraintID> to_remove = USE_NG2C ? new @Gen Vec<ConstraintID>() : new Vec<ConstraintID>();
        IVec<ConstraintID> hash_ids = USE_NG2C ? new @Gen Vec<ConstraintID>() : new Vec<ConstraintID>();
        if (getHashType() != HashFunctionType.NONE) {
            hash_ids = setHashFunction(solver, flattenLitVectors(flattenJobVars(this.job_vars)));
        }
        int nsols_in_cell = 0;
        int perm_gen;
        if (USE_NG2C) {
            perm_gen = System.getAllocGen();
            System.newAllocGen();
        }
        System.out.println("c Searching for a Pareto optimal solution");
        while (true) {
            checkSAT(solver);
            if (!solver.isSolved()) {
                printTimeoutMessage();
                return;
            }
            else if (solver.isSatisfiable()) {
                try {
                    ++nsols_in_cell;
                    int current_gen;
                    if (USE_NG2C) {
                        current_gen = System.getAllocGen();
                        System.setAllocGen(perm_gen);
                    }
                    saveSolution(modelToAllocation(solver,
                                                   this.instance.getPhysicalMachines(),
                                                   this.instance.getJobs(),
                                                   this.job_vars),
                                 true);
                    if (USE_NG2C) {
                        System.setAllocGen(current_gen);
                    }
                    BigDecimal energy_cost = computeEnergyConsumption(solver);
                    BigDecimal wastage = computeResourceWastage(solver);
                    to_remove.push(solver.addRemovableLessOrEqual(this.energy_lits,
                                                                  this.energy_coeffs,
                                                                  energy_cost));
                    to_remove.push(solver.addRemovableLessOrEqual(this.wastage_lits,
                                                                  this.wastage_coeffs,
                                                                  wastage));
                    BigInteger migration_cost = null;
                    if (this.instance.getMappings().size() > 0) {
                        migration_cost = computeMigrationCost(solver);
                        to_remove.push(solver.addRemovableLessOrEqual(this.migration_lits,
                                                                      this.migration_coeffs,
                                                                      migration_cost));
                    }
                    // Add constraints that force the next solution to dominate the current one
                    // FIXME: could be cleaner
                    if (USE_NG2C) {
                        System.setAllocGen(perm_gen);
                    }
                    IVecInt or_lits = new VecInt();
                    int new_var = newVar(solver);
                    this.energy_lits.push(new_var);
                    this.energy_coeffs.push(this.energy_coeff_sum.negate());
                    solver.addLess(this.energy_lits, this.energy_coeffs, energy_cost);
                    or_lits.push(-new_var);
                    this.energy_lits.pop();
                    this.energy_coeffs.pop();
                    assert(this.energy_coeffs.size() == this.energy_lits.size());
                    new_var = newVar(solver);
                    this.wastage_lits.push(new_var);
                    this.wastage_coeffs.push(this.wastage_coeff_sum.negate());
                    solver.addLess(this.wastage_lits, this.wastage_coeffs, wastage);
                    or_lits.push(-new_var);
                    this.wastage_lits.pop();
                    this.wastage_coeffs.pop();
                    assert(this.wastage_coeffs.size() == this.wastage_lits.size());
                    if (this.instance.getMappings().size() > 0) {
                        new_var = newVar(solver);
                        this.migration_lits.push(new_var);
                        this.migration_coeffs.push(this.migration_coeff_sum.negate());
                        solver.addLessOrEqual(this.migration_lits,
                                              this.migration_coeffs,
                                              migration_cost.subtract(BigInteger.ONE));
                        this.migration_lits.pop();
                        this.migration_coeffs.pop();
                        assert(this.migration_lits.size() == this.migration_coeffs.size());
                    }
                    solver.addClause(or_lits);
                    if (USE_NG2C) {
                        System.setAllocGen(current_gen);
                    }
                }
                catch (ContradictionException e) {
                    solver.removeConstraints(to_remove);
                    to_remove.clear();
                    printElapsedTime();
                    System.out.println("c Searching for another Pareto optimal solution");
                    // TODO: close epoch
                    if (USE_NG2C) {
                        System.newAllocGen();
                    }
                }
            }
            else if (to_remove.size() > 0) {
                solver.removeConstraints(to_remove);
                to_remove.clear();
                printElapsedTime();
                System.out.println("c Searching for another Pareto optimal solution");
                // TODO: close epoch
                if (USE_NG2C) {
                    System.newAllocGen();
                }
            }
            else if (getHashType() == HashFunctionType.NONE) {
                System.out.println("c Done");
                printElapsedTime();
                return;
            }
            else {
                nsols_in_cell = enum_threshold; // force new hash function
            }
            if (getHashType() != HashFunctionType.NONE && nsols_in_cell >= enum_threshold) {
                assert(hash_ids.size() > 0);
                solver.removeConstraints(hash_ids);
                to_remove = setHashFunction(solver, flattenLitVectors(flattenJobVars(this.job_vars)));
                nsols_in_cell = 0;
            }
        }
    }

}
