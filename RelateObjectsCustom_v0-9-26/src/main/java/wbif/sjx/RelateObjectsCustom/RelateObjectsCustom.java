package wbif.sjx.RelateObjectsCustom;

import ij.ImagePlus;
import wbif.sjx.MIA.Module.ImageProcessing.Pixel.Binary.DistanceMap;
import wbif.sjx.MIA.Module.ObjectProcessing.Identification.ProjectObjects;
import wbif.sjx.MIA.Module.ImageProcessing.Pixel.InvertIntensity;
import wbif.sjx.MIA.Module.ImageProcessing.Pixel.ProjectImage;
import wbif.sjx.MIA.Module.Module;
import wbif.sjx.MIA.Module.PackageNames;
import wbif.sjx.MIA.Object.*;
import wbif.sjx.MIA.Object.Parameters.*;
import wbif.sjx.common.Exceptions.IntegerOverflowException;
import wbif.sjx.common.Object.Point;
import wbif.sjx.common.Object.Volume;

import java.util.Iterator;

/**
 * Created by sc13967 on 04/05/2017.
 */
public class RelateObjectsCustom extends Module {
    public final static String INPUT_SEPARATOR = "Object input";
    public final static String PARENT_OBJECTS = "Parent (larger) objects";
    public final static String CHILD_OBJECTS = "Child (smaller) objects";

    public final static String RELATE_SEPARATOR = "Relation controls";
    public final static String RELATE_MODE = "Method to relate objects";
    public final static String REFERENCE_POINT = "Reference point";
    public final static String TEST_CHILD_OBJECTS = "Child objects to test against";
    public static final String LIMIT_LINKING_BY_DISTANCE = "Limit linking by distance";
    public final static String LINKING_DISTANCE = "Maximum linking distance (px)";
    public static final String INSIDE_OUTSIDE_MODE = "Inside/outside mode";
    public static final String MINIMUM_PERCENTAGE_OVERLAP = "Minimum percentage overlap";
    public static final String REQUIRE_CENTROID_OVERLAP = "Require centroid overlap";
    public final static String LINK_IN_SAME_FRAME = "Only link objects in same frame";
    public static final String THREE_D_MODE = "3D mode";

    public final static String OUTPUT_SEPARATOR = "Object output";
    public static final String MERGE_RELATED_OBJECTS = "Merge related objects";
    public static final String RELATED_OBJECTS = "Output overlapping objects";

    public interface RelateModes {
        String MATCHING_IDS = "Matching IDs";
        String PROXIMITY = "Proximity";
        String PROXIMITY_TO_CHILDREN = "Proximity to children";
        String SPATIAL_OVERLAP = "Spatial overlap";

        String[] ALL = new String[] { MATCHING_IDS, PROXIMITY, PROXIMITY_TO_CHILDREN, SPATIAL_OVERLAP };

    }

    public interface ReferencePoints {
        String CENTROID = "Centroid";
        String SURFACE = "Surface";
        String CENTROID_TO_SURFACE = "Child centroid to parent surface";

        String[] ALL = new String[] { CENTROID, SURFACE, CENTROID_TO_SURFACE };

    }

    public interface InsideOutsideModes {
        String INSIDE_AND_OUTSIDE = "Inside and outside";
        String INSIDE_ONLY = "Inside only (set outside to zero)";
        String OUTSIDE_ONLY = "Outside only (set inside to zero)";

        String[] ALL = new String[] { INSIDE_AND_OUTSIDE, INSIDE_ONLY, OUTSIDE_ONLY };

    }

    public interface ThreeDModes {
        String TWO_D = "2D (projection)";
        String SLICE_BY_SLICE = "Slice-by-slice (2.5D)";
        String THREE_D = "3D";

        String[] ALL = new String[] { TWO_D, SLICE_BY_SLICE, THREE_D };

    }

    public interface Measurements {
        String DIST_SURFACE_PX = "DIST_TO_${PARENT}_SURF_(PX)";
        String DIST_CENTROID_PX = "DIST_TO_${PARENT}_CENT_(PX)";
        String DIST_SURFACE_CAL = "DIST_TO_${PARENT}_SURF_(${CAL})";
        String DIST_CENTROID_CAL = "DIST_TO_${PARENT}_CENT_(${CAL})";
        String DIST_CENT_SURF_PX = "DIST_FROM_CENT_TO_${PARENT}_SURF_(PX)";
        String DIST_CENT_SURF_CAL = "DIST_FROM_CENT_TO_${PARENT}_SURF_(${CAL})";
        String DIST_CENT_SURF_FRAC = "DIST_FROM_CENT_TO_${PARENT}_SURF_(FRAC)";
        String OVERLAP_PC = "OVERLAP_WITH_${PARENT}_PERCENTAGE";

    }

    public static String getFullName(String measurement, String parentName, String threeDMode) {
        String measName = Units.replace("RELATE_OBJ // " + measurement.replace("${PARENT}", parentName));
        switch (threeDMode) {
            case ThreeDModes.TWO_D:
                measName = measName + "_(2D)";
                break;
            case ThreeDModes.SLICE_BY_SLICE:
                measName = measName + "_(2.5D)";
                break;
        }

        return measName;

    }

    public void linkMatchingIDs(ObjCollection parentObjects, ObjCollection childObjects) {
        for (Obj parentObject : parentObjects.values()) {
            int ID = parentObject.getID();

            Obj childObject = childObjects.get(ID);

            if (childObject != null) {
                parentObject.addChild(childObject);
                childObject.addParent(parentObject);

            }
        }
    }

    /*
     * Iterates over each testObject, calculating getting the smallest distance to a
     * parentObject. If this is smaller
     * than linkingDistance the link is assigned.
     */
    public void proximity(ObjCollection parentObjects, ObjCollection childObjects) {
        boolean linkInSameFrame = parameters.getValue(LINK_IN_SAME_FRAME);
        String referencePoint = parameters.getValue(REFERENCE_POINT);
        boolean limitLinking = parameters.getValue(LIMIT_LINKING_BY_DISTANCE);
        double linkingDistance = parameters.getValue(LINKING_DISTANCE);
        String threeDMode = parameters.getValue(THREE_D_MODE);

        if (!referencePoint.equals(ReferencePoints.CENTROID_TO_SURFACE)
                && !referencePoint.equals(ReferencePoints.SURFACE))
            threeDMode = ThreeDModes.THREE_D;

        String moduleName = RelateObjectsCustom.class.getSimpleName();

        int iter = 1;
        int numberOfChildren = childObjects.size();

        for (Obj childObject : childObjects.values()) {
            double minDist = Double.MAX_VALUE;
            Obj minLink = null;
            double dpp = childObject.getDistPerPxXY();

            Obj projChildObject = null;
            if (threeDMode.equals(ThreeDModes.TWO_D))
                try {
                    projChildObject = ProjectObjects.createProjection(childObject, "ProjChild", childObject.is2D());
                } catch (IntegerOverflowException e) {
                }

            for (Obj parentObject : parentObjects.values()) {
                if (linkInSameFrame & parentObject.getT() != childObject.getT())
                    continue;

                // Calculating the object spacing
                switch (referencePoint) {
                    case ReferencePoints.CENTROID:
                        double dist = childObject.getCentroidSeparation(parentObject, true);

                        if (dist < minDist) {
                            if (limitLinking && dist > linkingDistance)
                                continue;
                            minDist = dist;
                            minLink = parentObject;
                        }

                        break;

                    case ReferencePoints.SURFACE:
                        dist = 0;
                        switch (threeDMode) {
                            case ThreeDModes.TWO_D:
                                Obj projParentObject;
                                try {
                                    projParentObject = ProjectObjects.createProjection(parentObject, "ProjParent",
                                            parentObject.is2D());
                                    dist = projChildObject.getSurfaceSeparation(projParentObject, true);
                                } catch (IntegerOverflowException e) {
                                }
                                break;
                            case ThreeDModes.SLICE_BY_SLICE:
                                dist = new InPlaneSurfaceSeparationCalculator(childObject, parentObject, true)
                                        .getMinDist();
                                break;
                            case ThreeDModes.THREE_D:
                                dist = childObject.getSurfaceSeparation(parentObject, true);
                                break;
                        }

                        if (Math.abs(dist) < Math.abs(minDist)) {
                            if (limitLinking && Math.abs(dist) > linkingDistance)
                                continue;
                            minDist = dist;
                            minLink = parentObject;
                        }

                        break;

                    case ReferencePoints.CENTROID_TO_SURFACE:
                        double childXCent = childObject.getXMean(true);
                        double childYCent = childObject.getYMean(true);
                        double childZCent = childObject.getZMean(true, true);
                        double childZCentSlice = childObject.getZMean(true, false);

                        Point<Integer> currentPoint = new Point<>((int) Math.round(childXCent),
                                (int) Math.round(childYCent), (int) childZCentSlice);

                        double[] parentX = parentObject.getSurfaceX(true);
                        double[] parentY = parentObject.getSurfaceY(true);
                        double[] parentZ = parentObject.getSurfaceZ(true, true);

                        boolean isInside = false;

                        for (int i = 0; i < parentX.length; i++) {
                            double xDist = childXCent - parentX[i];
                            double yDist = childYCent - parentY[i];
                            double zDist = childZCent - parentZ[i];

                            switch (threeDMode) {
                                case ThreeDModes.TWO_D:
                                    // Z separation doesn't matter
                                    zDist = 0;
                                    break;
                                case ThreeDModes.SLICE_BY_SLICE:
                                    // Only process if in the same plane
                                    if (zDist != 0)
                                        continue;
                                    break;
                            }

                            dist = Math.sqrt(xDist * xDist + yDist * yDist + zDist * zDist);
                            if (dist < Math.abs(minDist)) {
                                if (limitLinking && dist > linkingDistance)
                                    continue;

                                minDist = dist;
                                minLink = parentObject;
                                isInside = parentObject.getPoints().contains(currentPoint);
                            }
                        }

                        // If this point is inside the parent the distance should be negative
                        if (isInside)
                            minDist = -minDist;

                        break;

                }
            }

            // If using centroid to surface proximity and inside only, calculate the
            // fractional distance
            if (referencePoint.equals(ReferencePoints.CENTROID_TO_SURFACE)
                    && parameters.getValue(INSIDE_OUTSIDE_MODE).equals(InsideOutsideModes.INSIDE_ONLY)) {
                calculateFractionalDistance(childObject, minLink, minDist, threeDMode);
            }

            // Applying the inside outside mode (doesn't apply for centroid-centroid
            // linking)
            if (referencePoint.equals(ReferencePoints.CENTROID_TO_SURFACE)
                    || referencePoint.equals(ReferencePoints.SURFACE)) {
                minDist = applyInsideOutsidePolicy(minDist);
            }

            // Adding measurements to the input object
            applyMeasurements(childObject, parentObjects, minDist, minLink, threeDMode);

            writeMessage("Processed " + (iter++) + " of " + numberOfChildren + " objects");

        }
    }

    public void calculateFractionalDistance(Obj childObject, Obj parentObject, double minDist, String threeDMode) {
        // Calculating the furthest distance to the edge
        if (parentObject.getMeasurement("MAX_DIST") == null) {
            // Creating an image for the parent object
            Image parentImage = parentObject.convertObjToImage("Parent");
            InvertIntensity.process(parentImage.getImagePlus());

            ImagePlus distIpl = DistanceMap.getDistanceMap(parentImage.getImagePlus(), true);

            Image projectedImage = ProjectImage.projectImageInZ(new Image("Dist", distIpl), "Projected",
                    ProjectImage.ProjectionModes.MAX);
            double maxDist = projectedImage.getImagePlus().getStatistics().max;

            parentObject.addMeasurement(new Measurement("MAX_DIST", maxDist));

        }

        // Adding measurement
        double maxDist = parentObject.getMeasurement("MAX_DIST").getValue();
        double frac = Math.abs(minDist / maxDist);
        String measurementName = getFullName(Measurements.DIST_CENT_SURF_FRAC, parentObject.getName(), threeDMode);
        childObject.addMeasurement(new Measurement(measurementName, frac));

    }

    public void applyMeasurements(Obj childObject, ObjCollection parentObjects, double minDist, Obj minLink,
            String threeDMode) {
        String referencePoint = parameters.getValue(REFERENCE_POINT);

        if (minLink != null) {
            double dpp = childObject.getDistPerPxXY();
            childObject.addParent(minLink);
            minLink.addChild(childObject);

            switch (referencePoint) {
                case ReferencePoints.CENTROID: {
                    String measurementName = getFullName(Measurements.DIST_CENTROID_PX, parentObjects.getName(),
                            threeDMode);
                    childObject.addMeasurement(new Measurement(measurementName, minDist));
                    measurementName = getFullName(Measurements.DIST_CENTROID_CAL, parentObjects.getName(), threeDMode);
                    childObject.addMeasurement(new Measurement(measurementName, minDist * dpp));

                    break;
                }
                case ReferencePoints.SURFACE: {
                    String measurementName = getFullName(Measurements.DIST_SURFACE_PX, parentObjects.getName(),
                            threeDMode);
                    childObject.addMeasurement(new Measurement(measurementName, minDist));
                    measurementName = getFullName(Measurements.DIST_SURFACE_CAL, parentObjects.getName(), threeDMode);
                    childObject.addMeasurement(new Measurement(measurementName, minDist * dpp));

                    break;
                }
                case ReferencePoints.CENTROID_TO_SURFACE: {
                    String measurementName = getFullName(Measurements.DIST_CENT_SURF_PX, parentObjects.getName(),
                            threeDMode);
                    childObject.addMeasurement(new Measurement(measurementName, minDist));
                    measurementName = getFullName(Measurements.DIST_CENT_SURF_CAL, parentObjects.getName(), threeDMode);
                    childObject.addMeasurement(new Measurement(measurementName, minDist * dpp));

                    break;
                }
            }

        } else {
            switch (referencePoint) {
                case ReferencePoints.CENTROID: {
                    String measurementName = getFullName(Measurements.DIST_CENTROID_PX, parentObjects.getName(),
                            threeDMode);
                    childObject.addMeasurement(new Measurement(measurementName, Double.NaN));
                    measurementName = getFullName(Measurements.DIST_CENTROID_CAL, parentObjects.getName(), threeDMode);
                    childObject.addMeasurement(new Measurement(measurementName, Double.NaN));

                    break;
                }
                case ReferencePoints.SURFACE: {
                    String measurementName = getFullName(Measurements.DIST_SURFACE_PX, parentObjects.getName(),
                            threeDMode);
                    childObject.addMeasurement(new Measurement(measurementName, Double.NaN));
                    measurementName = getFullName(Measurements.DIST_SURFACE_CAL, parentObjects.getName(), threeDMode);
                    childObject.addMeasurement(new Measurement(measurementName, Double.NaN));

                    break;
                }
                case ReferencePoints.CENTROID_TO_SURFACE: {
                    String measurementName = getFullName(Measurements.DIST_CENT_SURF_PX, parentObjects.getName(),
                            threeDMode);
                    childObject.addMeasurement(new Measurement(measurementName, Double.NaN));
                    measurementName = getFullName(Measurements.DIST_CENT_SURF_CAL, parentObjects.getName(), threeDMode);
                    childObject.addMeasurement(new Measurement(measurementName, Double.NaN));

                    break;
                }
            }
        }
    }

    public void proximityToChildren(ObjCollection parentObjects, ObjCollection childObjects) {
        String testChildObjectsName = parameters.getValue(TEST_CHILD_OBJECTS);
        boolean limitLinking = parameters.getValue(LIMIT_LINKING_BY_DISTANCE);
        double linkingDistance = parameters.getValue(LINKING_DISTANCE);

        // Runs through each child object against each parent object
        for (Obj parentObject : parentObjects.values()) {
            // Getting children of the parent to be used as references
            ObjCollection testChildren = parentObject.getChildren(testChildObjectsName);

            // Running through all proximal children
            for (Obj testChild : testChildren.values()) {
                // Getting centroid of the current child
                double xCentTest = testChild.getXMean(true);
                double yCentTest = testChild.getYMean(true);
                double zCentTest = testChild.getZMean(true, true);

                // Running through all children to relate
                for (Obj childObject : childObjects.values()) {
                    double xDist = xCentTest - childObject.getXMean(true);
                    double yDist = yCentTest - childObject.getYMean(true);
                    double zDist = zCentTest - childObject.getZMean(true, true);

                    // If the test object and the current object is less than the linking distance,
                    // assign the relationship
                    double dist = Math.sqrt(xDist * xDist + yDist * yDist + zDist * zDist);
                    if (limitLinking && dist <= linkingDistance) {
                        childObject.addParent(parentObject);
                        parentObject.addChild(childObject);

                    }
                }
            }
        }
    }

    public void spatialOverlap(ObjCollection parentObjects, ObjCollection childObjects, double minOverlap,
            boolean centroidOverlap, boolean linkInSameFrame) {

        int nCombined = parentObjects.size() * childObjects.size();
        int count = 0;
        String overlapMeasurementName = getFullName(Measurements.OVERLAP_PC, parentObjects.getName(),
                ThreeDModes.THREE_D);

        // Runs through each child object against each parent object
        for (Obj parentObject : parentObjects.values()) {
            for (Obj childObject : childObjects.values()) {
                // Testing if the two objects are in the same frame (if this matters)
                if (linkInSameFrame && parentObject.getT() != childObject.getT())
                    continue;

                // If requiring the child centroid is overlapped with the parent object
                if (centroidOverlap) {
                    int xCent = (int) Math.round(childObject.getXMean(true));
                    int yCent = (int) Math.round(childObject.getYMean(true));
                    int zCent = (int) Math.round(childObject.getZMean(true, false)); // Relates to image location
                    Point<Integer> centroid = new Point<>(xCent, yCent, zCent);

                    // If the centroid doesn't overlap, skip this link
                    if (!parentObject.containsPoint(centroid))
                        continue;

                }

                // Calculates the percentage overlap
                double nTotal = (double) childObject.getNVoxels();
                double nOverlap = (double) parentObject.getOverlap(childObject);
                double overlap = (nOverlap / nTotal) * 100;

                // Testing the minimum overlap requirement
                if (overlap == 0 || overlap < minOverlap)
                    continue;

                // If the tests are successful, add the link. If the child has already been
                // linked, but with a smaller
                // overlap, remove that link.
                Obj oldParent = childObject.getParent(parentObject.getName());
                if (oldParent != null) {
                    if (childObject.getMeasurement(overlapMeasurementName).getValue() < overlap) {
                        oldParent.removeChild(childObject);
                    } else {
                        // If the previous link had a better overlap, skip the assignment
                        continue;
                    }
                }

                // Creating the link
                parentObject.addChild(childObject);
                childObject.addParent(parentObject);

                // Adding the overlap as a measurement
                Measurement measurement = new Measurement(
                        getFullName(Measurements.OVERLAP_PC, parentObject.getName(), ThreeDModes.THREE_D));
                measurement.setValue(overlap);
                childObject.addMeasurement(measurement);

            }

            writeMessage("Compared " + (childObjects.size() * ++count) + " of " + nCombined + " pairs");

        }
    }

    public double applyInsideOutsidePolicy(double minDist) {
        String insideOutsideMode = parameters.getValue(INSIDE_OUTSIDE_MODE);

        switch (insideOutsideMode) {
            case InsideOutsideModes.INSIDE_AND_OUTSIDE:
                return minDist;

            case InsideOutsideModes.INSIDE_ONLY:
                return Math.min(0, minDist);

            case InsideOutsideModes.OUTSIDE_ONLY:
                return Math.max(0, minDist);

        }

        return 0;

    }

    public ObjCollection mergeRelatedObjects(ObjCollection parentObjects, ObjCollection childObjects,
            String relatedObjectsName) {
        Obj exampleParent = parentObjects.getFirst();
        ObjCollection relatedObjects = new ObjCollection(relatedObjectsName);

        if (exampleParent == null)
            return relatedObjects;

        double dppXY = exampleParent.getDistPerPxXY();
        double dppZ = exampleParent.getDistPerPxZ();
        String calibratedUnits = exampleParent.getCalibratedUnits();
        boolean twoD = exampleParent.is2D();

        Iterator<Obj> parentIterator = parentObjects.values().iterator();
        while (parentIterator.hasNext()) {
            Obj parentObj = parentIterator.next();

            // Collecting all children for this parent. If none are present, skip to the
            // next parent
            ObjCollection currChildObjects = parentObj.getChildren(childObjects.getName());
            if (currChildObjects.size() == 0)
                continue;

            // Creating a new Obj and assigning pixels from the parent and all children
            Obj relatedObject = new Obj(relatedObjectsName, relatedObjects.getAndIncrementID(), dppXY, dppZ,
                    calibratedUnits, twoD);
            relatedObject.setT(parentObj.getT());
            relatedObjects.add(relatedObject);

            for (Obj childObject : currChildObjects.values()) {
                // Transferring points from the child object to the new object
                relatedObject.getPoints().addAll(childObject.getPoints());

                // Removing the child object from its original collection
                childObjects.values().remove(childObject);

            }

            // Transferring points from the parent object to the new object
            relatedObject.getPoints().addAll(parentObj.getPoints());

            // Removing the parent object from its original collection
            parentIterator.remove();

        }

        return relatedObjects;

    }

    @Override
    public String getTitle() {
        return "Relate objects (custom)";

    }

    @Override
    public String getPackageName() {
        return PackageNames.OBJECT_PROCESSING_IDENTIFICATION;
    }

    @Override
    public String getHelp() {
        return "";
    }

    @Override
    public boolean process(Workspace workspace) {
        // Getting input objects
        String parentObjectName = parameters.getValue(PARENT_OBJECTS);
        ObjCollection parentObjects = workspace.getObjects().get(parentObjectName);

        String childObjectName = parameters.getValue(CHILD_OBJECTS);
        ObjCollection childObjects = workspace.getObjects().get(childObjectName);

        // Getting parameters
        String relateMode = parameters.getValue(RELATE_MODE);
        boolean linkInSameFrame = parameters.getValue(LINK_IN_SAME_FRAME);
        String testChildObjectsName = parameters.getValue(TEST_CHILD_OBJECTS);
        String referencePoint = parameters.getValue(REFERENCE_POINT);
        boolean limitLinking = parameters.getValue(LIMIT_LINKING_BY_DISTANCE);
        double linkingDistance = parameters.getValue(LINKING_DISTANCE);
        double minOverlap = parameters.getValue(MINIMUM_PERCENTAGE_OVERLAP);
        boolean centroidOverlap = parameters.getValue(REQUIRE_CENTROID_OVERLAP);
        boolean mergeRelatedObjects = parameters.getValue(MERGE_RELATED_OBJECTS);
        String relatedObjectsName = parameters.getValue(RELATED_OBJECTS);

        switch (relateMode) {
            case RelateModes.MATCHING_IDS:
                writeMessage("Relating objects by matching ID numbers");
                linkMatchingIDs(parentObjects, childObjects);
                break;

            case RelateModes.PROXIMITY:
                writeMessage("Relating objects by proximity");
                proximity(parentObjects, childObjects);
                break;

            case RelateModes.PROXIMITY_TO_CHILDREN:
                writeMessage("Relating objects by proximity to children");
                proximityToChildren(parentObjects, childObjects);
                break;

            case RelateModes.SPATIAL_OVERLAP:
                writeMessage("Relating objects by spatial overlap");
                spatialOverlap(parentObjects, childObjects, minOverlap, centroidOverlap, linkInSameFrame);
                break;

        }

        if (mergeRelatedObjects) {
            ObjCollection relatedObjects = mergeRelatedObjects(parentObjects, childObjects, relatedObjectsName);
            if (relatedObjects != null)
                workspace.addObjects(relatedObjects);

        }

        return true;

    }

    @Override
    protected void initialiseParameters() {
        parameters.add(new ParamSeparatorP(INPUT_SEPARATOR, this));
        parameters.add(new InputObjectsP(PARENT_OBJECTS, this));
        parameters.add(new InputObjectsP(CHILD_OBJECTS, this));

        parameters.add(new ParamSeparatorP(RELATE_SEPARATOR, this));
        parameters.add(new ChoiceP(RELATE_MODE, this, RelateModes.MATCHING_IDS, RelateModes.ALL));
        parameters.add(new ChoiceP(REFERENCE_POINT, this, ReferencePoints.CENTROID, ReferencePoints.ALL));
        parameters.add(new ChildObjectsP(TEST_CHILD_OBJECTS, this));
        parameters.add(new BooleanP(LIMIT_LINKING_BY_DISTANCE, this, false));
        parameters.add(new DoubleP(LINKING_DISTANCE, this, 1.0));
        parameters.add(
                new ChoiceP(INSIDE_OUTSIDE_MODE, this, InsideOutsideModes.INSIDE_AND_OUTSIDE, InsideOutsideModes.ALL));
        parameters.add(new DoubleP(MINIMUM_PERCENTAGE_OVERLAP, this, 0d));
        parameters.add(new BooleanP(REQUIRE_CENTROID_OVERLAP, this, true));
        parameters.add(new BooleanP(LINK_IN_SAME_FRAME, this, true));
        parameters.add(new ChoiceP(THREE_D_MODE, this, ThreeDModes.THREE_D, ThreeDModes.ALL));

        parameters.add(new ParamSeparatorP(OUTPUT_SEPARATOR, this));
        parameters.add(new BooleanP(MERGE_RELATED_OBJECTS, this, false));
        parameters.add(new OutputObjectsP(RELATED_OBJECTS, this));

    }

    @Override
    public ParameterCollection updateAndGetParameters() {
        ParameterCollection returnedParameters = new ParameterCollection();

        returnedParameters.add(parameters.getParameter(INPUT_SEPARATOR));
        returnedParameters.add(parameters.getParameter(PARENT_OBJECTS));
        returnedParameters.add(parameters.getParameter(CHILD_OBJECTS));

        returnedParameters.add(parameters.getParameter(RELATE_SEPARATOR));
        returnedParameters.add(parameters.getParameter(RELATE_MODE));

        String referencePoint = parameters.getValue(REFERENCE_POINT);
        switch ((String) parameters.getValue(RELATE_MODE)) {
            case RelateModes.PROXIMITY:
                returnedParameters.add(parameters.getParameter(REFERENCE_POINT));
                returnedParameters.add(parameters.getParameter(LIMIT_LINKING_BY_DISTANCE));
                if ((boolean) parameters.getValue(LIMIT_LINKING_BY_DISTANCE)) {
                    returnedParameters.add(parameters.getParameter(LINKING_DISTANCE));
                }

                if (referencePoint.equals(ReferencePoints.CENTROID_TO_SURFACE)
                        || referencePoint.equals(ReferencePoints.SURFACE)) {
                    returnedParameters.add(parameters.getParameter(INSIDE_OUTSIDE_MODE));
                    returnedParameters.add(parameters.getParameter(THREE_D_MODE));
                }

                break;

            case RelateModes.PROXIMITY_TO_CHILDREN:
                returnedParameters.add(parameters.getParameter(TEST_CHILD_OBJECTS));
                returnedParameters.add(parameters.getParameter(LIMIT_LINKING_BY_DISTANCE));
                if ((boolean) parameters.getValue(LIMIT_LINKING_BY_DISTANCE)) {
                    returnedParameters.add(parameters.getParameter(LINKING_DISTANCE));
                }

                if (referencePoint.equals(ReferencePoints.CENTROID_TO_SURFACE)
                        || referencePoint.equals(ReferencePoints.SURFACE)) {
                    returnedParameters.add(parameters.getParameter(INSIDE_OUTSIDE_MODE));
                }

                String parentObjectNames = parameters.getValue(PARENT_OBJECTS);
                ((ChildObjectsP) parameters.getParameter(TEST_CHILD_OBJECTS)).setParentObjectsName(parentObjectNames);

                break;

            case RelateModes.SPATIAL_OVERLAP:
                returnedParameters.add(parameters.getParameter(MINIMUM_PERCENTAGE_OVERLAP));
                returnedParameters.add(parameters.getParameter(REQUIRE_CENTROID_OVERLAP));
                break;
        }

        returnedParameters.add(parameters.getParameter(LINK_IN_SAME_FRAME));

        returnedParameters.add(parameters.getParameter(OUTPUT_SEPARATOR));
        returnedParameters.add(parameters.getParameter(MERGE_RELATED_OBJECTS));
        if ((boolean) parameters.getValue(MERGE_RELATED_OBJECTS)) {
            returnedParameters.add(parameters.getParameter(RELATED_OBJECTS));
        }

        return returnedParameters;

    }

    @Override
    public MeasurementRefCollection updateAndGetImageMeasurementRefs() {
        return null;
    }

    @Override
    public MeasurementRefCollection updateAndGetObjectMeasurementRefs(ModuleCollection modules) {
        objectMeasurementRefs.setAllCalculated(false);

        String childObjectsName = parameters.getValue(CHILD_OBJECTS);
        String parentObjectName = parameters.getValue(PARENT_OBJECTS);
        String referencePoint = parameters.getValue(REFERENCE_POINT);
        String threeDMode = parameters.getValue(THREE_D_MODE);

        if (!referencePoint.equals(ReferencePoints.CENTROID_TO_SURFACE)
                && !referencePoint.equals(ReferencePoints.SURFACE))
            threeDMode = ThreeDModes.THREE_D;

        if (parentObjectName == null || childObjectsName == null)
            return objectMeasurementRefs;

        String measurementName = getFullName(Measurements.DIST_SURFACE_PX, parentObjectName, threeDMode);
        MeasurementRef distSurfPx = objectMeasurementRefs.getOrPut(measurementName);
        distSurfPx.setDescription("Shortest distance between the surface of this object and that of the closest \""
                + parentObjectName + "\" object.  Negative values indicate this object is inside the relevant \""
                + parentObjectName + "\" object. Measured in pixel units.");

        measurementName = getFullName(Measurements.DIST_SURFACE_CAL, parentObjectName, threeDMode);
        MeasurementRef distSurfCal = objectMeasurementRefs.getOrPut(measurementName);

        measurementName = getFullName(Measurements.DIST_CENTROID_PX, parentObjectName, threeDMode);
        MeasurementRef distCentPx = objectMeasurementRefs.getOrPut(measurementName);
        distCentPx.setDescription("Distance between the centroid of this object and that of the closest \""
                + parentObjectName + "\"object.  Measured in pixel units.");

        measurementName = getFullName(Measurements.DIST_CENTROID_CAL, parentObjectName, threeDMode);
        MeasurementRef distCentCal = objectMeasurementRefs.getOrPut(measurementName);

        measurementName = getFullName(Measurements.DIST_CENT_SURF_PX, parentObjectName, threeDMode);
        MeasurementRef distCentSurfPx = objectMeasurementRefs.getOrPut(measurementName);
        distCentSurfPx.setDescription("Shortest distance between the centroid of this object and the surface of the " +
                "closest \"" + parentObjectName + "\" object.  Negative values indicate this object is inside the " +
                "relevant \"" + parentObjectName + "\" object. Measured in pixel units.");

        measurementName = getFullName(Measurements.DIST_CENT_SURF_CAL, parentObjectName, threeDMode);
        MeasurementRef distCentSurfCal = objectMeasurementRefs.getOrPut(measurementName);

        measurementName = getFullName(Measurements.DIST_CENT_SURF_FRAC, parentObjectName, threeDMode);
        MeasurementRef distCentSurfFrac = objectMeasurementRefs.getOrPut(measurementName);
        distCentSurfFrac
                .setDescription("Shortest distance between the centroid of this object and the surface of the " +
                        "closest \"" + parentObjectName
                        + "\" object.  Calculated as a fraction of the furthest possible distance " +
                        "to the \"" + parentObjectName + "\" surface.");

        measurementName = getFullName(Measurements.OVERLAP_PC, parentObjectName, ThreeDModes.THREE_D);
        MeasurementRef overlapPercentage = objectMeasurementRefs.getOrPut(measurementName);
        overlapPercentage
                .setDescription("Percentage of pixels that overlap with the \"" + parentObjectName + "\" object " +
                        "with which it has the largest overlap.");

        distSurfPx.setImageObjName(childObjectsName);
        distCentPx.setImageObjName(childObjectsName);
        distSurfCal.setImageObjName(childObjectsName);
        distCentCal.setImageObjName(childObjectsName);
        distCentSurfPx.setImageObjName(childObjectsName);
        distCentSurfCal.setImageObjName(childObjectsName);
        distCentSurfFrac.setImageObjName(childObjectsName);
        overlapPercentage.setImageObjName(childObjectsName);

        distCentPx.setCalculated(false);
        distCentCal.setCalculated(false);
        distSurfPx.setCalculated(false);
        distSurfCal.setCalculated(false);
        distCentSurfPx.setCalculated(false);
        distCentSurfCal.setCalculated(false);
        distCentSurfFrac.setCalculated(false);
        overlapPercentage.setCalculated(false);

        switch ((String) parameters.getValue(RELATE_MODE)) {
            case RelateModes.PROXIMITY:
                switch ((String) parameters.getValue(REFERENCE_POINT)) {
                    case ReferencePoints.CENTROID:
                        distCentPx.setCalculated(true);
                        distCentCal.setCalculated(true);
                        break;

                    case ReferencePoints.SURFACE:
                        distSurfPx.setCalculated(true);
                        distSurfCal.setCalculated(true);
                        break;

                    case ReferencePoints.CENTROID_TO_SURFACE:
                        distCentSurfPx.setCalculated(true);
                        distCentSurfCal.setCalculated(true);

                        if (parameters.getValue(INSIDE_OUTSIDE_MODE).equals(InsideOutsideModes.INSIDE_ONLY)) {
                            distCentSurfFrac.setCalculated(true);
                        }
                        break;
                }
                break;

            case RelateModes.SPATIAL_OVERLAP:
                overlapPercentage.setCalculated(true);
                break;
        }

        return objectMeasurementRefs;

    }

    @Override
    public MetadataRefCollection updateAndGetMetadataReferences() {
        return null;
    }

    @Override
    public RelationshipCollection updateAndGetRelationships() {
        RelationshipCollection relationships = new RelationshipCollection();

        relationships.addRelationship(parameters.getValue(PARENT_OBJECTS), parameters.getValue(CHILD_OBJECTS));

        return relationships;

    }

}

class InPlaneSurfaceSeparationCalculator {
    private final double minDist;
    private final Point<Integer> p1;
    private final Point<Integer> p2;

    public InPlaneSurfaceSeparationCalculator(Volume v1, Volume v2, boolean pixelDistances) {
        double minDist = Double.MAX_VALUE;
        Point<Integer> p1 = null;
        Point<Integer> p2 = null;
        double[] x1 = v1.getSurfaceX(pixelDistances);
        double[] y1 = v1.getSurfaceY(pixelDistances);
        double[] z1 = v1.getSurfaceZ(pixelDistances, true);
        double[] z1Slice = v1.getSurfaceZ(true, false);
        double[] x2 = v2.getSurfaceX(pixelDistances);
        double[] y2 = v2.getSurfaceY(pixelDistances);
        double[] z2 = v2.getSurfaceZ(pixelDistances, true);
        double[] z2Slice = v2.getSurfaceZ(true, false);

        for (int j = 0; j < x2.length; ++j) {
            Point<Integer> currentPoint2 = new Point((int) x2[j], (int) y2[j], (int) z2Slice[j]);
            boolean isInside = false;

            for (int i = 0; i < x1.length; ++i) {
                double xDist = x2[j] - x1[i];
                double yDist = y2[j] - y1[i];
                double zDist = z2[j] - z1[i];

                if (zDist != 0)
                    continue;

                double dist = Math.sqrt(xDist * xDist + yDist * yDist + zDist * zDist);
                if (dist < Math.abs(minDist)) {
                    minDist = dist;
                    p1 = new Point((int) x1[i], (int) y1[i], (int) z1Slice[i]);
                    p2 = new Point((int) x2[j], (int) y2[j], (int) z2Slice[j]);
                    isInside = v1.getPoints().contains(currentPoint2);
                    if (!isInside) {
                        Point<Integer> currentPoint1 = new Point((int) x1[i], (int) y1[i], (int) z1Slice[i]);
                        isInside = v2.getPoints().contains(currentPoint1);
                    }
                }
            }

            if (isInside) {
                minDist = -minDist;
            }
        }

        this.minDist = minDist;
        this.p1 = p1;
        this.p2 = p2;
    }

    public double getMinDist() {
        return this.minDist;
    }

    public Point<Integer> getP1() {
        return this.p1;
    }

    public Point<Integer> getP2() {
        return this.p2;
    }
}