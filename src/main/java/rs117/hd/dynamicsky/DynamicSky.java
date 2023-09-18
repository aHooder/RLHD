package rs117.hd.dynamicsky;

import rs117.hd.dynamicsky.hosek.ArHosekSkyModelState;

import static java.lang.Math.PI;
import static rs117.hd.dynamicsky.hosek.ArHosekSkyModel.arhosek_rgb_skymodelstate_alloc_init;
import static rs117.hd.dynamicsky.hosek.ArHosekSkyModel.arhosek_tristim_skymodel_radiance;

public class DynamicSky {
	public static void main(String... args) {
		double turbidity = 10; // "haziness" from 1 to 10, with 50 being foggy, but unsupported by the model
		double albedo = 0.09; // grass
		double solarElevation = PI / 6; // radians

		ArHosekSkyModelState model = arhosek_rgb_skymodelstate_alloc_init(turbidity, albedo, solarElevation);

		double theta = PI / 3; // angle from zenith
		double gamma = 0; // azimuthal angle
		double result = arhosek_tristim_skymodel_radiance(model, theta, gamma, 0);

		System.out.printf("result: %f\n", result);
	}
}
