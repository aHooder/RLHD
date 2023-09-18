/*
This source is published under the following 3-clause BSD license.

Copyright (c) 2012 - 2013, Lukas Hosek and Alexander Wilkie
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* None of the names of the contributors may be used to endorse or promote
  products derived from this software without specific prior written
  permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package rs117.hd.dynamicsky.hosek;

import com.google.gson.Gson;
import java.io.IOException;

import static rs117.hd.utils.ResourcePath.path;

public class ArHosekSkyModelData_Spectral {
	static double[][] datasets;
	static double[][] datasetsRad;
	static double[][] solarDatasets;
	static double[][] limbDarkeningDatasets;

	public static void loadDatasets(Gson gson) throws IOException {
		if (datasets != null)
			return;

		var data = path(ArHosekSkyModelData_Spectral.class, "spectral-dataset.jsonc")
			.loadJson(gson, ArHosekSkyModelData_Spectral.class);

		datasets = new double[][] {
			data.dataset320,
			data.dataset360,
			data.dataset400,
			data.dataset440,
			data.dataset480,
			data.dataset520,
			data.dataset560,
			data.dataset600,
			data.dataset640,
			data.dataset680,
			data.dataset720
		};

		datasetsRad = new double[][] {
			data.datasetRad320,
			data.datasetRad360,
			data.datasetRad400,
			data.datasetRad440,
			data.datasetRad480,
			data.datasetRad520,
			data.datasetRad560,
			data.datasetRad600,
			data.datasetRad640,
			data.datasetRad680,
			data.datasetRad720
		};

		solarDatasets = new double[][] {
			data.solarDataset320,
			data.solarDataset360,
			data.solarDataset400,
			data.solarDataset440,
			data.solarDataset480,
			data.solarDataset520,
			data.solarDataset560,
			data.solarDataset600,
			data.solarDataset640,
			data.solarDataset680,
			data.solarDataset720
		};

		limbDarkeningDatasets = new double[][] {
			data.limbDarkeningDataset320,
			data.limbDarkeningDataset360,
			data.limbDarkeningDataset400,
			data.limbDarkeningDataset440,
			data.limbDarkeningDataset480,
			data.limbDarkeningDataset520,
			data.limbDarkeningDataset560,
			data.limbDarkeningDataset600,
			data.limbDarkeningDataset640,
			data.limbDarkeningDataset680,
			data.limbDarkeningDataset720
		};
	}

	public static void clearDatasets() {
		datasets = datasetsRad = solarDatasets = limbDarkeningDatasets = null;
	}

	// Loaded from JSON
	double[] dataset320;
	double[] dataset360;
	double[] dataset400;
	double[] dataset440;
	double[] dataset480;
	double[] dataset520;
	double[] dataset560;
	double[] dataset600;
	double[] dataset640;
	double[] dataset680;
	double[] dataset720;
	double[] datasetRad320;
	double[] datasetRad360;
	double[] datasetRad400;
	double[] datasetRad440;
	double[] datasetRad480;
	double[] datasetRad520;
	double[] datasetRad560;
	double[] datasetRad600;
	double[] datasetRad640;
	double[] datasetRad680;
	double[] datasetRad720;
	double[] solarDataset320;
	double[] solarDataset360;
	double[] solarDataset400;
	double[] solarDataset440;
	double[] solarDataset480;
	double[] solarDataset520;
	double[] solarDataset560;
	double[] solarDataset600;
	double[] solarDataset640;
	double[] solarDataset680;
	double[] solarDataset720;
	double[] limbDarkeningDataset320;
	double[] limbDarkeningDataset360;
	double[] limbDarkeningDataset400;
	double[] limbDarkeningDataset440;
	double[] limbDarkeningDataset480;
	double[] limbDarkeningDataset520;
	double[] limbDarkeningDataset560;
	double[] limbDarkeningDataset600;
	double[] limbDarkeningDataset640;
	double[] limbDarkeningDataset680;
	double[] limbDarkeningDataset720;
}
