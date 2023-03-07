package de.domisum.lib.compitum.navmesh.pathfinding.traversal;

import de.domisum.lib.auxilium.data.container.math.LineSegment3D;
import de.domisum.lib.auxilium.data.container.math.Vector3D;
import de.domisum.lib.auxilium.util.time.ProfilerStopWatch;
import de.domisum.lib.compitum.navmesh.geometry.NavMeshTriangle;
import de.domisum.lib.compitum.navmesh.transition.NavMeshLadder;
import de.domisum.lib.compitum.navmesh.transition.NavMeshTrianglePortal;
import de.domisum.lib.compitum.navmesh.transition.NavMeshTriangleTransition;
import de.domisum.lib.compitum.path.Path;
import de.domisum.lib.compitum.path.PathWaypoint;
import de.domisum.lib.compitum.path.node.TransitionType;

import java.util.ArrayList;
import java.util.List;

public class NavMeshTriangleTraverser {

    // INPUT
    private Vector3D startPosition;
    private Vector3D targetPosition;
    private List<NavMeshTriangle> triangleSequence;

    // STATUS
    private List<PathWaypoint> pathWaypoints = new ArrayList<>();
    // triangle traversal
    private Vector3D currentPosition;
    private Vector3D visLeft;
    private Vector3D visRight;
    private int visLeftTriangleIndex;
    private int visRightTriangleIndex;

    private int currentTriangleIndex = 0;
    private NavMeshTriangle triangle;
    private NavMeshTriangle triangleAfter;

    private Vector3D portalEndpointLeft;
    private Vector3D portalEndpointRight;

    private ProfilerStopWatch stopWatch = new ProfilerStopWatch("pathfinding.navMesh.triangleTraversal");

    // OUTPUT
    private Path path;

    // INIT
    public NavMeshTriangleTraverser(Vector3D startPosition, Vector3D targetPosition, List<NavMeshTriangle> triangleSequence) {
        this.startPosition = startPosition;
        this.targetPosition = targetPosition;

        this.triangleSequence = triangleSequence;
    }

    // GETTERS
    public Path getPath() {
        return this.path;
    }

    public ProfilerStopWatch getStopWatch() {
        return this.stopWatch;
    }

    private Vector3D getTowardsVisLeft() {
        return this.visLeft.subtract(this.currentPosition);
    }

    private Vector3D getTowardsVisRight() {
        return this.visRight.subtract(this.currentPosition);
    }

    private Vector3D getTowardsPortalEndpointLeft() {
        return this.portalEndpointLeft.subtract(this.currentPosition);
    }

    private Vector3D getTowardsPortalEndpointRight() {
        return this.portalEndpointRight.subtract(this.currentPosition);
    }

    // TRAVERSAL
    public void traverseTriangles() {
        this.stopWatch.start();

        this.currentPosition = this.startPosition;

        if (this.triangleSequence.size() == 1) {
            this.pathWaypoints.add(new PathWaypoint(this.targetPosition, TransitionType.WALK));
        } else {
            for (this.currentTriangleIndex = 0;
                    this.currentTriangleIndex < this.triangleSequence.size(); this.currentTriangleIndex++) {
                processTriangleTransition();
            }
        }

        this.path = new Path(this.pathWaypoints);
        this.stopWatch.stop();
    }

    private void processTriangleTransition() {
        this.triangle = this.triangleSequence.get(this.currentTriangleIndex);
        this.triangleAfter = this.currentTriangleIndex + 1 < this.triangleSequence.size()
                ? this.triangleSequence.get(this.currentTriangleIndex + 1)
                : null;

        NavMeshTriangleTransition transition = this.triangle.getTransitionTo(this.triangleAfter);

        if (this.triangleAfter == null) {
            traverseTrianglePortal();
        } else if (transition.getTransitionType() == TransitionType.WALK) {
            traverseTrianglePortal();
        } else if (transition.getTransitionType() == TransitionType.CLIMB) {
            useLadder();
        }
    }

    // WALKING
    private void traverseTrianglePortal() {
        if (this.triangleAfter == null) // last triangle
        {
            processMovementTowardsTargetPoint(this.targetPosition);
        } // either first triangle processing or after new corner
        else if (this.visLeft == null) // if visLeft is null, then visRight is also null
        {
            findPortalEndpoints(this.triangle, this.triangleAfter);
            this.visLeft = this.portalEndpointLeft;
            this.visRight = this.portalEndpointRight;
            this.visLeftTriangleIndex = this.currentTriangleIndex;
            this.visRightTriangleIndex = this.currentTriangleIndex;
        } else {
            findPortalEndpoints(this.triangle, this.triangleAfter);

            boolean leftSame = isSame(this.visLeft, this.currentPosition);
            boolean rightSame = isSame(this.visRight, this.currentPosition);

            // check if portal is out on one side
            if (isLeftOf(getTowardsVisRight(), getTowardsPortalEndpointLeft(), true) && !leftSame && !rightSame) // right turn
            {
                newWaypoint(this.visRight, TransitionType.WALK);

                this.currentTriangleIndex = this.visRightTriangleIndex;
                return;
            } else if (isLeftOf(getTowardsPortalEndpointRight(), getTowardsVisLeft(), true) && !leftSame && !rightSame) // left turn
            {
                newWaypoint(this.visLeft, TransitionType.WALK);

                this.currentTriangleIndex = this.visLeftTriangleIndex;
                return;
            }

            // confine movement cone
            if (isLeftOf(getTowardsVisLeft(), getTowardsPortalEndpointLeft(), true)) // left
            {
                this.visLeft = this.portalEndpointLeft;
                this.visLeftTriangleIndex = this.currentTriangleIndex;
            }
            if (isLeftOf(getTowardsPortalEndpointRight(), getTowardsVisRight(), true)) // right
            {
                this.visRight = this.portalEndpointRight;
                this.visRightTriangleIndex = this.currentTriangleIndex;
            }
        }
    }

    private void processMovementTowardsTargetPoint(Vector3D targetPoint) {
        // the vis points can be null if the transition into the previous triangle was a turn
        // if this is the case, the target point is guaranteed to be in the cone
        if (this.visLeft != null) {
            Vector3D towardsTargetPoint = targetPoint.subtract(this.currentPosition);

            if (isLeftOf(getTowardsVisRight(), towardsTargetPoint, false)) // right turn
            {
                newWaypoint(this.visRight, TransitionType.WALK);

                this.currentTriangleIndex = this.visRightTriangleIndex;
                return;
            } else if (isLeftOf(towardsTargetPoint, getTowardsVisLeft(), false)) // left turn
            {
                newWaypoint(this.visLeft, TransitionType.WALK);

                this.currentTriangleIndex = this.visLeftTriangleIndex;
                return;
            }
        }

        this.pathWaypoints.add(new PathWaypoint(targetPoint, TransitionType.WALK));
    }

    // LADDER CLIMBING
    private void useLadder() {
        NavMeshTriangleTransition transition = this.triangle.getTransitionTo(this.triangleAfter);
        NavMeshLadder ladder = (NavMeshLadder) transition;

        boolean upwards = ladder.getTriangleBottom() == this.triangle;
        if (upwards) {
            processMovementTowardsTargetPoint(ladder.getPositionBottom());
            Vector3D climbingEndPosition = new Vector3D(ladder.getPositionBottom().x, ladder.getPositionTop().y,
                    ladder.getPositionBottom().z);

            PathWaypoint climbPathWaypoint = newWaypoint(climbingEndPosition, TransitionType.CLIMB);
            climbPathWaypoint.setData("ladderDirection", ladder.getLadderDirection());
            newWaypoint(ladder.getPositionTop(), TransitionType.WALK);
        } else {
            Vector3D climbingStartPosition = new Vector3D(ladder.getPositionBottom().x, ladder.getPositionTop().y,
                    ladder.getPositionBottom().z);

            processMovementTowardsTargetPoint(climbingStartPosition);
            PathWaypoint climbPathWaypoint = newWaypoint(ladder.getPositionBottom(), TransitionType.CLIMB);
            climbPathWaypoint.setData("ladderDirection", ladder.getLadderDirection());
        }
    }

    // SUBROUTINES
    private void findPortalEndpoints(NavMeshTriangle from, NavMeshTriangle to) {
        NavMeshTriangleTransition transition = from.getTransitionTo(to);
        LineSegment3D portalLineSegment = ((NavMeshTrianglePortal) transition).getFullLineSegment();

        this.portalEndpointLeft = portalLineSegment.a;
        this.portalEndpointRight = portalLineSegment.b;

        Vector3D fromCenter = from.getCenter();
        if (isLeftOf(this.portalEndpointRight.subtract(fromCenter), this.portalEndpointLeft.subtract(fromCenter), false)) {
            Vector3D temp = this.portalEndpointLeft;
            this.portalEndpointLeft = this.portalEndpointRight;
            this.portalEndpointRight = temp;
        }
    }

    private PathWaypoint newWaypoint(Vector3D position, int transitionType) {
        PathWaypoint pathWaypoint = new PathWaypoint(position, transitionType);
        this.pathWaypoints.add(pathWaypoint);

        this.currentPosition = position;
        this.visLeft = null;
        this.visRight = null;

        return pathWaypoint;
    }

    // UTIL
    private static boolean isLeftOf(Vector3D v1, Vector3D v2, boolean onZero) {
        double crossY = v1.crossProduct(v2).y;

        if (crossY == 0) {
            return onZero;
        }

        return crossY < 0;
    }

    private static boolean isSame(Vector3D a, Vector3D b) {
        if (a == null) {
            return b == null;
        }

        return a.equals(b);
    }

}
