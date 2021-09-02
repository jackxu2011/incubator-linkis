/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webank.wedatasphere.linkis.engineconnplugin.flink.client.sql.operation.impl;

import com.webank.wedatasphere.linkis.engineconnplugin.flink.client.config.Environment;
import com.webank.wedatasphere.linkis.engineconnplugin.flink.client.context.ExecutionContext;
import com.webank.wedatasphere.linkis.engineconnplugin.flink.client.sql.operation.NonJobOperation;
import com.webank.wedatasphere.linkis.engineconnplugin.flink.client.sql.operation.OperationUtil;
import com.webank.wedatasphere.linkis.engineconnplugin.flink.client.sql.operation.result.ColumnInfo;
import com.webank.wedatasphere.linkis.engineconnplugin.flink.client.sql.operation.result.ConstantNames;
import com.webank.wedatasphere.linkis.engineconnplugin.flink.client.sql.operation.result.ResultKind;
import com.webank.wedatasphere.linkis.engineconnplugin.flink.client.sql.operation.result.ResultSet;
import com.webank.wedatasphere.linkis.engineconnplugin.flink.context.FlinkEngineConnContext;
import com.webank.wedatasphere.linkis.engineconnplugin.flink.exception.SqlExecutionException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.shaded.guava18.com.google.common.collect.ImmutableMap;
import org.apache.flink.table.types.logical.VarCharType;
import org.apache.flink.types.Row;

/**
 * Operation for SET command.
 */
public class SetOperation implements NonJobOperation {
	private final FlinkEngineConnContext context;
	private final String key;
	private final String value;

	public SetOperation(FlinkEngineConnContext context, String key, String value) {
		this.context = context;
		this.key = key;
		this.value = value;
	}

	public SetOperation(FlinkEngineConnContext context) {
		this(context, null, null);
	}

	@Override
	public ResultSet execute() throws SqlExecutionException {
		ExecutionContext executionContext = context.getExecutionContext();
		Environment env = executionContext.getEnvironment();

		// list all properties
		if (key == null) {
			List<Row> data = new ArrayList<>();
			Tuple2<Integer, Integer> maxKeyLenAndMaxValueLen = new Tuple2<>(1, 1);
			buildResult(env.getExecution().asTopLevelMap(), data, maxKeyLenAndMaxValueLen);
			buildResult(env.getDeployment().asTopLevelMap(), data, maxKeyLenAndMaxValueLen);
			buildResult(env.getConfiguration().asMap(), data, maxKeyLenAndMaxValueLen);

			return ResultSet.builder()
				.resultKind(ResultKind.SUCCESS_WITH_CONTENT)
				.columns(
					ColumnInfo.create(ConstantNames.SET_KEY, new VarCharType(true, maxKeyLenAndMaxValueLen.f0)),
					ColumnInfo.create(ConstantNames.SET_VALUE, new VarCharType(true, maxKeyLenAndMaxValueLen.f1)))
				.data(data)
				.build();
		} else {
			// TODO avoid to build a new Environment for some cases
			// set a property
			Environment newEnv = Environment.enrich(env, ImmutableMap.of(key.trim(), value.trim()), ImmutableMap.of());
			ExecutionContext.SessionState sessionState = executionContext.getSessionState();

			// Renew the ExecutionContext by new environment.
			// Book keep all the session states of current ExecutionContext then
			// re-register them into the new one.
			ExecutionContext newExecutionContext = context
				.newExecutionContextBuilder(context.getEnvironmentContext().getDefaultEnv())
				.env(newEnv)
				.sessionState(sessionState)
				.build();
			context.setExecutionContext(newExecutionContext);

			return OperationUtil.OK;
		}
	}

	private void buildResult(
		Map<String, String> properties,
		List<Row> data,
		Tuple2<Integer, Integer> maxKeyLenAndMaxValueLen) {
		for (Map.Entry<String, String> entry : properties.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			data.add(Row.of(key, value));
			// update max key length
			maxKeyLenAndMaxValueLen.f0 = Math.max(maxKeyLenAndMaxValueLen.f0, key.length());
			// update max value length
			maxKeyLenAndMaxValueLen.f1 = Math.max(maxKeyLenAndMaxValueLen.f1, value.length());
		}
	}
}
