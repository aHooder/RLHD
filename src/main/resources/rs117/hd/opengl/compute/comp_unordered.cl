/*
 * Copyright (c) 2021, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

#include cl_types.cl

__kernel
__attribute__((reqd_work_group_size(6, 1, 1)))
void passthroughModel(
  __global const struct ModelInfo *ol,
  __global const int4 *vb,
  __global const float4 *uv,
  __global const float4 *normal,
  __global int4 *vout,
  __global float4 *uvout,
  __global float4 *normalout
) {
  size_t groupId = get_group_id(0);
  size_t localId = get_local_id(0);
  struct ModelInfo minfo = ol[groupId];

  int offset = minfo.offset;
  int outOffset = minfo.idx;
  int uvOffset = minfo.uvOffset;

  int4 pos = (int4)(minfo.x, minfo.y, minfo.z, 0);

  if (localId >= (size_t) minfo.size) {
    return;
  }

  uint ssboOffset = localId;
  int4 thisA, thisB, thisC;

  thisA = pos + vb[offset + ssboOffset * 3];
  thisB = pos + vb[offset + ssboOffset * 3 + 1];
  thisC = pos + vb[offset + ssboOffset * 3 + 2];

  uint myOffset = localId;

  // position vertices in scene and write to out buffer
  vout[outOffset + myOffset * 3]     = thisA;
  vout[outOffset + myOffset * 3 + 1] = thisB;
  vout[outOffset + myOffset * 3 + 2] = thisC;

  if (uvOffset < 0) {
    uvout[outOffset + myOffset * 3]     = (float4)(0.0f, 0.0f, 0.0f, 0.0f);
    uvout[outOffset + myOffset * 3 + 1] = (float4)(0.0f, 0.0f, 0.0f, 0.0f);
    uvout[outOffset + myOffset * 3 + 2] = (float4)(0.0f, 0.0f, 0.0f, 0.0f);
  } else {
    uvout[outOffset + myOffset * 3]     = (float4)(convert_float2(thisA.xz) / 128.f, 0, uv[uvOffset + localId * 3].w);
    uvout[outOffset + myOffset * 3 + 1] = (float4)(convert_float2(thisB.xz) / 128.f, 0, uv[uvOffset + localId * 3 + 1].w);
    uvout[outOffset + myOffset * 3 + 2] = (float4)(convert_float2(thisC.xz) / 128.f, 0, uv[uvOffset + localId * 3 + 2].w);
  }
  
  float4 normA, normB, normC;
  
  normA = normal[offset + ssboOffset * 3    ];
  normB = normal[offset + ssboOffset * 3 + 1];
  normC = normal[offset + ssboOffset * 3 + 2];
  
  normalout[outOffset + myOffset * 3]     = normA;
  normalout[outOffset + myOffset * 3 + 1] = normB;
  normalout[outOffset + myOffset * 3 + 2] = normC;
}
