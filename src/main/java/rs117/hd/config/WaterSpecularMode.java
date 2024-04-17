package rs117.hd.config;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public enum WaterSpecularMode {
	OFF,
	SUN,
	LIGHTS,
	SUN_AND_LIGHTS("Both");

	private String name;

	@Override
	public String toString() {
		if (name != null)
			return name;
		return name();
	}
}
