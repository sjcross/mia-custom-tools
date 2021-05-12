package wbif.sjx.AutoSubstack;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import wbif.sjx.MIA.MIA;
import wbif.sjx.MIA.Module.Module;
import wbif.sjx.MIA.Module.PackageNames;
import wbif.sjx.MIA.Module.ImageProcessing.Stack.ExtractSubstack;
import wbif.sjx.MIA.Object.Image;
import wbif.sjx.MIA.Object.MeasurementRefCollection;
import wbif.sjx.MIA.Object.MetadataRefCollection;
import wbif.sjx.MIA.Object.ModuleCollection;
import wbif.sjx.MIA.Object.RelationshipCollection;
import wbif.sjx.MIA.Object.Workspace;
import wbif.sjx.MIA.Object.Parameters.InputImageP;
import wbif.sjx.MIA.Object.Parameters.OutputImageP;
import wbif.sjx.MIA.Object.Parameters.ParamSeparatorP;
import wbif.sjx.MIA.Object.Parameters.ParameterCollection;

public class AutoSubstack_0_9_26 extends Module {
    public static void main(String[] args) throws Exception {
        MIA.addPluginPackageName(AutoSubstack_0_9_26.class.getCanonicalName());
        MIA.main(new String[] {});

    }

    public static final String INPUT_SEPARATOR = "Image input/output";
    public static final String INPUT_IMAGE = "Input image";
    public static final String OUTPUT_IMAGE = "Output image";

    @Override
    public String getTitle() {
        return "Auto substack (v0.9.26)";
    }

    @Override
    public String getPackageName() {
        return PackageNames.IMAGE_PROCESSING_STACK;
    }

    @Override
    public String getHelp() {
        return null;
    }

    public static int findMinSlice(ImagePlus ipl) {
        ImageStack ist = ipl.getStack();

        for (int z = 0; z < ipl.getNSlices(); z++) {
            for (int t = 0; t < ipl.getNFrames(); t++) {
                for (int c = 0; c < ipl.getNChannels(); c++) {
                    int idx = ipl.getStackIndex(c + 1, z + 1, t + 1);
                    ImageProcessor ipr = ist.getProcessor(idx);

                    if (ipr.getStatistics().max > 0)
                        return z;
                }
            }
        }

        // If all slices are empty, return -1
        return -1;

    }

    @Override
    protected boolean process(Workspace workspace) {
        // Getting parameters
        String inputImageName = parameters.getValue(INPUT_IMAGE);
        String outputImageName = parameters.getValue(OUTPUT_IMAGE);

        // Getting input image
        Image inputImage = workspace.getImages().get(inputImageName);
        ImagePlus ipl = inputImage.getImagePlus();

        // Finding lowest slice with non-zero intensity
        int minSlice = findMinSlice(ipl);

        // Extracting substack
        Image outputImage = null;
        if (minSlice == -1)
            outputImage = new Image(outputImageName,ipl.duplicate());
        else
            outputImage = ExtractSubstack.extractSubstack(inputImage, outputImageName, "1-end", (minSlice+1)+"-end", "1-end");

        // Adding image to workspace
        workspace.addImage(outputImage);

        if (showOutput)
            outputImage.showImage();

        return true;

    }

    @Override
    protected void initialiseParameters() {
        parameters.add(new ParamSeparatorP(INPUT_SEPARATOR, this));
        parameters.add(new InputImageP(INPUT_IMAGE, this));
        parameters.add(new OutputImageP(OUTPUT_IMAGE, this));

    }

    @Override
    public ParameterCollection updateAndGetParameters() {
        return parameters;

    }

    @Override
    public MetadataRefCollection updateAndGetMetadataReferences() {
        return null;
    }

    @Override
    public MeasurementRefCollection updateAndGetImageMeasurementRefs() {
        return null;
    }

    @Override
    public MeasurementRefCollection updateAndGetObjectMeasurementRefs(ModuleCollection arg0) {
        return null;
    }

    @Override
    public RelationshipCollection updateAndGetRelationships() {
        return null;
    }
}
