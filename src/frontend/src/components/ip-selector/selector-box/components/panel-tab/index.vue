<template>
    <vuedraggable
        :animation="200"
        class="ip-selector-panel-tab"
        :disabled="false"
        ghost-class="ghost"
        group="description"
        :list="panelSortList"
        @change="handleSortChange">
        <template v-for="(panelName) in panelSortList">
            <tab-item
                v-if="panelName === 'staticTopo'"
                :key="`${panelName}_staticTopo`"
                :active="value === 'staticTopo'"
                :disabled="isUnqiuePanelValue && uniqueType !== 'staticTopo'"
                @change="handleChange('staticTopo')">
                <span>静态拓扑</span>
            </tab-item>
            <tab-item
                v-else-if="panelName === 'dynamicTopo'"
                :key="`${panelName}_dynamicTopo`"
                :active="value === 'dynamicTopo'"
                :disabled="isUnqiuePanelValue && uniqueType !== 'dynamicTopo'"
                @change="handleChange('dynamicTopo')">
                <span>动态拓扑</span>
            </tab-item>
            <tab-item
                v-else-if="panelName === 'dynamicGroup'"
                :key="`${panelName}_dynamicGroup`"
                :active="value === 'dynamicGroup'"
                :disabled="isUnqiuePanelValue && uniqueType !== 'dynamicGroup'"
                @change="handleChange('dynamicGroup')">
                <span>动态分组</span>
            </tab-item>
            <tab-item
                v-else-if="panelName === 'serviceTemplate'"
                :key="`${panelName}_serviceTemplate`"
                :active="value === 'serviceTemplate'"
                :disabled="isUnqiuePanelValue && uniqueType !== 'serviceTemplate'"
                @change="handleChange('serviceTemplate')">
                <span>服务模板</span>
            </tab-item>
            <tab-item
                v-else-if="panelName === 'setTemplate'"
                :key="`${panelName}_setTemplate`"
                :active="value === 'setTemplate'"
                :disabled="isUnqiuePanelValue && uniqueType !== 'setTemplate'"
                @change="handleChange('setTemplate')">
                <span>集群模板</span>
            </tab-item>
            <tab-item
                v-else-if="panelName === 'manualInput'"
                :key="`${panelName}_manualInput`"
                :active="value === 'manualInput'"
                :disabled="isUnqiuePanelValue && uniqueType !== 'staticTopo'"
                @change="handleChange('manualInput')">
                <span>手动输入</span>
            </tab-item>
        </template>
    </vuedraggable>
</template>
<script setup>
    import _ from 'lodash';
    import {
        computed,
        ref,
        watch,
     } from 'vue';
    import vuedraggable from 'vuedraggable';

    import Manager from '../../../manager';
    import { makeMap } from '../../../utils';

    import TabItem from './tab-item.vue';

    const props = defineProps({
        isShow: {
            type: Boolean,
            default: false,
        },
        value: {
            type: String,
            required: true,
        },
        uniqueType: {
            type: String,
        },
    });

    const emits = defineEmits([
        'change',
        'update:value',
    ]);

    const CUSTOM_SETTINGS_MODULE = Manager.nameStyle(`ipSelectorPanelTab${Manager.config.version}`);

    const {
        unqiuePanelValue,
        panelList,
    } = Manager.config;

    const panelSortList = ref([...panelList]);

    const isUnqiuePanelValue = computed(() => Boolean(props.uniqueType) && unqiuePanelValue);

    Manager.service.fetchCustomSettings({
        [Manager.nameStyle('moduleList')]: [CUSTOM_SETTINGS_MODULE],
    })
    .then((data) => {
        if (data[CUSTOM_SETTINGS_MODULE]
            && data[CUSTOM_SETTINGS_MODULE].panelSortList) {
            const panelConfigMap = makeMap(panelList);

            if (data[CUSTOM_SETTINGS_MODULE].panelSortList.length === panelSortList.value.length) {
                const newPanelList = data[CUSTOM_SETTINGS_MODULE].panelSortList.reduce((result, item) => {
                    if (panelConfigMap[item]) {
                        result.push(item);
                        delete panelConfigMap[item];
                    }
                    return result;
                }, []);
                newPanelList.push(_.uniq(...Object.keys(panelConfigMap)));

                panelSortList.value = newPanelList;
            }
        }
    })
    .finally(() => {
        setTimeout(() => {
            handleChange(props.uniqueType ? props.uniqueType : panelSortList.value[0]);
        });
    });

    watch(() => props.isShow, () => {
        setTimeout(() => {
            if (props.isShow) {
                handleChange(props.uniqueType ? props.uniqueType : panelSortList.value[0]);
            }
        });
    });

    const handleSortChange = (data) => {
        Manager.service.updateCustomSettings({
            [Manager.nameStyle('settingsMap')]: {
                [CUSTOM_SETTINGS_MODULE]: {
                    panelSortList: panelSortList.value,
                },
            },
        });
    };

    // 切换
    const handleChange = (value) => {
        if (value === props.value) {
            return;
        }
        emits('change', value);
        emits('update:value', value);
    };
</script>
<style lang="postcss">
    .ip-selector-panel-tab {
        display: flex;
        background: #fafbfd;
        border-bottom: 1px solid #dcdee5;
        user-select: none;

        .ip-selector-panel-tab-item {
            position: relative;
            display: flex;
            align-items: center;
            justify-content: center;
            flex: 1;
            height: 40px;
            color: #63656e;
            text-align: center;
            cursor: pointer;
            border-right: 1px solid #dcdee5;

            &:hover {
                .drag-btn {
                    opacity: 100%;
                }
            }

            &.active {
                position: relative;
                color: #313238;
                cursor: default;
                background: #fff;

                &::after {
                    position: absolute;
                    bottom: -2px;
                    left: 0;
                    width: 100%;
                    height: 3px;
                    background: #fff;
                    content: "";
                }
            }

            &.disabled {
                color: #c4c6cc;
                cursor: not-allowed;
            }

            .drag-btn {
                position: absolute;
                left: 10px;
                font-size: 12px;
                color: #979ba5;
                cursor: move;
                opacity: 0%;
                transition: all 0.15s;
            }

            &:last-child {
                border-right: none;
            }
        }

        .ghost {
            background: #eaebf0;

            & > * {
                display: none;
            }

            &::before {
                font-size: 12px;
                color: #c4c6cc;
                content: "放在这里";
            }
        }
    }
</style>
