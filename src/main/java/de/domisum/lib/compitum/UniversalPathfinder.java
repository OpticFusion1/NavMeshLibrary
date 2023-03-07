package de.domisum.lib.compitum;

import de.domisum.lib.auxilium.util.java.annotations.API;
import de.domisum.lib.compitum.block.BlockAStar;
import de.domisum.lib.compitum.block.BlockPathSmoother;
import de.domisum.lib.compitum.navmesh.NavMesh;
import de.domisum.lib.compitum.navmesh.NavMeshManager;
import de.domisum.lib.compitum.navmesh.pathfinding.NavMeshPathfinder;
import de.domisum.lib.compitum.path.BlockPath;
import de.domisum.lib.compitum.path.Path;
import org.bukkit.Location;

import java.util.Objects;

@API
public class UniversalPathfinder {

    // INPUT
    private Location start;
    private Location target;

    // PROPERTIES
    // OUTPUT
    private Path path;

    private String diagnose;
    private String failure;

    // INIT
    @API
    public UniversalPathfinder(Location start, Location target) {
        this.start = fixPathfindingLocation(start);
        this.target = fixPathfindingLocation(target);
    }

    // GETTERS
    @API
    public Path getPath() {
        return this.path;
    }

    @API
    public boolean isPathFound() {
        return this.path != null;
    }

    @API
    public String getDiagnose() {
        return this.diagnose;
    }

    @API
    public String getFailure() {
        return this.failure;
    }

    // PATHFINDING
    @API
    public void findPath() {
        navMeshCheck:
        if (CompitumLib.areNavMeshesEnabled()) {
            NavMeshManager nmm = CompitumLib.getNavMeshManager();
            NavMesh meshAtStart = nmm.getNavMeshAt(this.start);
            if (meshAtStart == null) {
                break navMeshCheck;
            }

            NavMesh meshAtTarget = nmm.getNavMeshAt(this.target);
            if (meshAtTarget == null) {
                break navMeshCheck;
            }

            if (!Objects.equals(meshAtStart, meshAtTarget)) {
                break navMeshCheck;
            }

            useNavMesh(meshAtStart);
            return;
        }

        useWorldAStar();
    }

    private void useWorldAStar() {
        BlockAStar pathfinder = new BlockAStar(this.start, this.target);
        pathfinder.findPath();
        this.diagnose = pathfinder.getDiagnose();
        if (!pathfinder.pathFound()) {
            this.failure = pathfinder.getFailure();
            return;
        }
        BlockPath blockPath = pathfinder.getPath();

        BlockPathSmoother smoother = new BlockPathSmoother(blockPath);
        smoother.convert();
        this.path = smoother.getSmoothPath();
    }

    private void useNavMesh(NavMesh navMesh) {
        NavMeshPathfinder pathfinder = new NavMeshPathfinder(this.start, this.target, navMesh);
        pathfinder.findPath();
        this.path = pathfinder.getPath();

        //this.diagnose = pathfinder.getDiagnose();
        if (this.path == null) {
            this.failure = getFailure();
            return;
        }

        this.path = pathfinder.getPath();
    }

    @API
    public static Location fixPathfindingLocation(Location location) {
        location.setY(Math.floor(location.getY()));

        String materialName = location.getBlock().getType().name();
        if (materialName.contains("SLAB") || materialName.contains("STEP")) {
            location.add(0, 1, 0);
        }

        return location;
    }

}
