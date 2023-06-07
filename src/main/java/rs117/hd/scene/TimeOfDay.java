package rs117.hd.scene;

import java.time.Instant;
import net.runelite.http.api.worlds.WorldRegion;
import rs117.hd.utils.SunCalc;

public enum TimeOfDay
{
	DAY,
	NIGHT,
	DUSK_DAWN,
	;

	private static final long MS_PER_MINUTE = 60000;
	private static final float NIGHT_RANGE = 2f;

	public static TimeOfDay getTimeOfDay(double[] latLong, int dayLength)
	{
		Instant modifiedDate = getModifiedDate(Instant.now(), dayLength);

		double[] angles = SunCalc.getPosition(modifiedDate.toEpochMilli(), latLong[0], latLong[1]);
		angles[1] = Math.PI - angles[1]; // Flip the direction of rotation, since Gielinor rotates opposite to Earth

		if (isNight(angles[1]))
		{
			return TimeOfDay.NIGHT;
		}
		else if (isDuskDawn(angles[1]))
		{
			return TimeOfDay.DUSK_DAWN;
		}
		else
		{
			return TimeOfDay.DAY;
		}
	}

	/**
	 * Get the current sun or moon angles for a given set of coordinates and simulated day length in minutes.
	 * @param latLong array of latitude and longitude coordinates
	 * @param dayLength in minutes per day
	 * @return the azimuth and altitude angles in radians
	 * @see <a href="https://en.wikipedia.org/wiki/Horizontal_coordinate_system">Horizontal coordinate system</a>
	 */
	public static double[] getCurrentAngles(double[] latLong, int dayLength)
	{
		Instant modifiedDate = getModifiedDate(Instant.now(), dayLength);

		double[] angles = SunCalc.getPosition(modifiedDate.toEpochMilli(), latLong[0], latLong[1]);
		angles[1] = Math.PI - angles[1]; // Flip the direction of rotation, since Gielinor rotates opposite to Earth

		if (isNight(angles[1]))
		{
			// Switch to moon angles
			angles = SunCalc.getMoonPosition(modifiedDate.toEpochMilli(), latLong[0], latLong[1]);
			angles[1] = Math.PI - angles[1]; // Flip the direction of rotation, since the moon orbits opposite to Earth's moon
		}

		return angles;
	}

	public static float getLightStrength(double[] latLong, int dayLength) {
		Instant modifiedDate = getModifiedDate(Instant.now(), dayLength);

		double[] angles = SunCalc.getPosition(modifiedDate.toEpochMilli(), latLong[0], latLong[1]);
		double[] moonAngles = SunCalc.getMoonPosition(modifiedDate.toEpochMilli(), latLong[0], latLong[1]);

		float strength;

		if (isNight(angles[1]))
			strength = SunCalc.getMoonStrength(moonAngles);
		else
			strength = SunCalc.getStrength(angles);

		return strength;
	}

	public static float[] getLightColor(double[] latLong, int dayLength) {
		Instant modifiedDate = getModifiedDate(Instant.now(), dayLength);

		return SunCalc.getColor(modifiedDate.toEpochMilli(), latLong);
	}

	public static Instant getModifiedDate(Instant currentDate, int dayLength)
	{
		long millisPerDay = dayLength * MS_PER_MINUTE;
		long elapsedMillis = currentDate.toEpochMilli() % millisPerDay;
		double speedFactor = 1440.0 / dayLength; // Speed factor to adjust the speed of time
		return currentDate.plusMillis((long) (elapsedMillis * speedFactor));
	}

	public static double[] getLatLong(WorldRegion currentRegion)
	{
		double latitude;
		double longitude;
		switch (currentRegion)
		{
			case AUSTRALIA:
				// Sydney
				latitude = -33.8478035;
				longitude = 150.6016588;
				break;
			case GERMANY:
				// Berlin
				latitude = 52.5063843;
				longitude = 13.0944281;
				break;
			case UNITED_STATES_OF_AMERICA:
				// Chicago
				latitude = 41.8337329;
				longitude = -87.7319639;
				break;
			default:
			case UNITED_KINGDOM:
				// Cambridge
				latitude = 52.1951;
				longitude = 0.1313;
				break;
		}
		return new double[]{latitude, longitude};
	}

	private static boolean isNight(double altitude) {
		double angleFromZenith = Math.abs(altitude - Math.PI / 2);
		return angleFromZenith > Math.PI / NIGHT_RANGE;
	}

	private static boolean isDuskDawn(double altitude) {
		double angleFromZenith = Math.abs(altitude - Math.PI / 2);
		return angleFromZenith > Math.PI / 2.45;
	}
}
