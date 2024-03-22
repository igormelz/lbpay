#!/bin/bash
__lic=$(cat <<EOF
/*
 * Copyright 2021-2024 OpenFS.RU
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
EOF
)

for fn in `find src/main/java/ru/openfs/lbpay  -name '*.java'`
do
	echo -n $fn
	islic=$(head -1 $fn)
    if [[ "$islic" =~ ^package.*$ ]]; then
		echo "$__lic" > 1
		cat $fn >> 1
		mv 1 $fn
		echo " Updated"
	else
		echo " LIC"
	fi
done
