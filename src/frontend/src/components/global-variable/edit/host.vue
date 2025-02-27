<!--
 * Tencent is pleased to support the open source community by making BK-JOB蓝鲸智云作业平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-JOB蓝鲸智云作业平台 is licensed under the MIT License.
 *
 * License for BK-JOB蓝鲸智云作业平台:
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
-->

<template>
    <div
        class="variable-type-host"
        :class="{ 'variable-value-error': isError }">
        <div>
            <div>
                <bk-button
                    v-bk-tooltips="descPopover"
                    class="mr10"
                    :disabled="readonly"
                    @click="handleChooseIp">
                    <Icon type="plus" />
                    {{ $t('添加服务器') }}
                </bk-button>
                <bk-button
                    v-show="isNotEmpty"
                    :disabled="readonly"
                    @click="handleClear">
                    {{ $t('清空') }}
                </bk-button>
            </div>
            <ip-selector
                :origianl-value="originalHostNodeInfo"
                :show-dialog="isShowChooseIp"
                show-view
                :value="hostNodeInfo"
                @change="handleChange"
                @close-dialog="handleCloseIpSelector" />
            <!-- <server-panel
                v-show="isNotEmpty"
                ref="choostIP"
                class="host-value-panel"
                :host-node-info="hostNodeInfo"
                detail-fullscreen
                :editable="!readonly"
                @on-change="handleChange" /> -->
            <p
                v-if="isError"
                class="variable-error">
                {{ $t('该变量的值必填') }}
            </p>
        </div>
        <!-- <choose-ip
            v-model="isShowChooseIp"
            :host-node-info="hostNodeInfo"
            @on-change="handleChange" /> -->
    </div>
</template>
<script>
    import _ from 'lodash';

    import TaskHostNodeModel from '@model/task-host-node';
    // import ChooseIp from '@components/choose-ip';
    // import ServerPanel from '@components/choose-ip/server-panel';

    export default {
        // components: {
        //     ChooseIp,
        //     ServerPanel,
        // },
        props: {
            data: {
                type: Object,
                required: true,
            },
            readonly: {
                type: Boolean,
                default: false,
            },
        },
        data () {
            return {
                isShowChooseIp: false,
                hostNodeInfo: {},
                originalHostNodeInfo: {},
            };
        },
        computed: {
            isNotEmpty () {
                return !TaskHostNodeModel.isHostNodeInfoEmpty(this.hostNodeInfo);
            },
            isError () {
                if (this.data.required !== 1) {
                    return false;
                }
                return !this.isNotEmpty;
            },
            descPopover () {
                return {
                    theme: 'light',
                    extCls: 'variable-desc-tippy',
                    trigger: 'click mouseenter',
                    hideOnClick: false,
                    content: this.data.description,
                    disabled: !this.data.description,
                };
            },
        },
        created () {
            this.init();
        },
        methods: {
            /**
             * @desc 解析默认值
             */
            init () {
                if (this.data.defaultTargetValue) {
                    this.hostNodeInfo = this.data.defaultTargetValue.hostNodeInfo;
                } else {
                    this.hostNodeInfo = this.data.targetValue.hostNodeInfo;
                }
                this.originalHostNodeInfo = Object.freeze(_.cloneDeep(this.hostNodeInfo));
            },
            /**
             * @desc 外部调用——移除无效主机
             */
            removeAllInvalidHost () {
                window.changeFlag = true;
                this.$refs.choostIP.removeAllInvalidHost();
            },
            /**
             * @desc 编辑主机列表
             */
            handleChooseIp () {
                this.isShowChooseIp = true;
            },
            handleCloseIpSelector () {
                this.isShowChooseIp = false;
            },
            /**
             * @desc 清空主机列表
             */
            handleClear () {
                const { hostNodeInfo } = new TaskHostNodeModel({});
                this.hostNodeInfo = hostNodeInfo;
                window.changeFlag = true;
            },
            /**
             * @desc 提交编辑的数据
             */
            handleChange (hostNodeInfo) {
                this.hostNodeInfo = Object.freeze(hostNodeInfo);
                window.changeFlag = true;
            },
            /**
             * @desc 外部调用——还原默认值
             */
            reset () {
                this.init();
            },
            /**
             * @desc 外部调用——值验证
             * @returns {Promise}
             */
            validate () {
                const { type, id, name } = this.data;

                const data = {
                    id,
                    name,
                    type,
                    value: '',
                    targetValue: {
                        hostNodeInfo: this.hostNodeInfo,
                    },
                };
                return new Promise((resolve, reject) => {
                    if (this.isError) {
                        return reject(new Error('host error'));
                    }
                    resolve(data);
                });
            },
        },
    };
</script>
<style lang='postcss' scoped>
    .variable-type-host {
        .host-value-panel {
            margin-top: 10px;
        }
    }

</style>
