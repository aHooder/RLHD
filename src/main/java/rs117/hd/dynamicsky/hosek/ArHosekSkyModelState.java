package rs117.hd.dynamicsky.hosek;

public class ArHosekSkyModelState {
	public double[][] configs = new double[11][9];
	public double[] radiances = new double[11];
	public double turbidity;
	public double solar_radius;
	public double[] emission_correction_factor_sky = new double[11];
	public double[] emission_correction_factor_sun = new double[11];
	public double albedo;
	public double elevation;
}
