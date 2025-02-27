<template>
    <div
        ref="rootRef"
        class="bk-ip-selector">
        <template v-if="localMounted">
            <selector-box
                :is-show="showDialog"
                :mode="mode"
                :value="selectorValue"
                @cancel="handleCancel"
                @change="handleValueChange">
                <template #description>
                    <slot name="dialogFooterDescription" />
                </template>
            </selector-box>
            <views-box
                v-if="showView"
                ref="viewsRef"
                :search-key="viewSearchKey"
                :value="selectorValue"
                @change="handleValueChange" />
        </template>
    </div>
</template>
<script setup>
    import {
        onBeforeUnmount,
        onMounted,
        provide,
        ref,
        shallowRef,
        watch,
    } from 'vue';

    import {
        mergeLocalConfig,
        mergeLocalService,
    } from './manager.js';
    import SelectorBox from './selector-box/index.vue';
    import {
        formatInput,
        formatOutput,
    } from './utils/index';
    import ViewsBox from './views-box/index.vue';

    import './bk-icon/style.css';
    import './bk-icon/iconcool.js';
    import 'tippy.js/dist/tippy.css';
    import 'tippy.js/themes/light.css';

    const props = defineProps({
        mode: {
            type: String,
            default: 'dialog', // 'dialog' | 'section'
        },
        height: {
            type: Number,
        },
        showDialog: {
            type: Boolean,
            default: false,
        },
        showView: {
            type: Boolean,
            default: false,
        },
        value: {
            type: Object,
            validator: () => true,
        },
        originalValue: {
            type: Object,
        },
        showViewDiff: {
            type: Boolean,
            default: false,
        },
        viewSearchKey: {
            type: String,
        },
        readonly: {
            type: Boolean,
            default: false,
        },
        disableDialogSubmitMethod: {
            type: Function,
        },
        disableHostMethod: {
            type: Function,
        },
        service: {
            type: Object,
            default: () => ({}),
        },
        config: {
            type: Object,
            default: () => ({}),
        },
    });

    const emits = defineEmits([
        'change',
        'close-dialog',
    ]);

    const rootRef = ref();
    const viewsRef = ref();
    const localMounted = ref(false);
    const selectorValue = shallowRef({});

    watch(() => props.value, () => {
        selectorValue.value = props.value;
    }, {
        immediate: true,
    });

    const handleValueChange = (value) => {
        selectorValue.value = value;
        emits('change', value);
        emits('update:value', value);
        emits('update:modelalue', value);
        emits('close-dialog');
    };

    const handleCancel = () => {
        emits('close-dialog');
    };

    // provide('BKIPSELECTOR', {
    //     originalValue: props.originalValue && formatInput(props.originalValue),
    //     readonly: props.readonly,
    //     mode: props.mode,
    //     disableDialogSubmitMethod: props.disableDialogSubmitMethod,
    //     disableHostMethod: props.disableHostMethod,
    //     rootRef,
    // });
    provide('BKIPSELECTOR', new Proxy({}, {
        get (target, propKey) {
            const allowProps = [
                'readonly',
                'mode',
                'height',
                'disableDialogSubmitMethod',
                'disableHostMethod',
            ];
            if (propKey === 'originalValue') {
                return props.originalValue && formatInput(props.originalValue);
            }
            if (allowProps.includes(propKey)) {
                return props[propKey];
            }
            if (propKey === 'rootRef') {
                return rootRef;
            }
            return undefined;
        },
    }));

    onMounted(() => {
        setTimeout(() => {
            mergeLocalService(props.service);
            mergeLocalConfig(props.config);
            localMounted.value = true;
        });
    });
    onBeforeUnmount(() => {
        mergeLocalService({});
        mergeLocalConfig({});
    });

    defineExpose({
        getHostList () {
            if (!viewsRef.value) {
                return [];
            }
            return viewsRef.value.getHostList();
        },
        getHostIpList () {
            if (!viewsRef.value) {
                return [];
            }
            return viewsRef.value.getHostIpList();
        },
        getAbnormalHostIpList () {
            if (!viewsRef.value) {
                return [];
            }
            return viewsRef.value.getAbnormalHostIpList();
        },
        resetValue () {
            handleValueChange(formatOutput({
                hostList: [],
                nodeList: [],
                dynamicGroupList: [],
            }));
        },
        refresh () {
            viewsRef.value && viewsRef.value.refresh();
        },
        collapseToggle (toggle) {
            viewsRef.value && viewsRef.value.collapseToggle(toggle);
        },
    });
</script>
<style lang="postcss">
    @keyframes bk-ip-selector-rotate-loading {
        0% {
            transform: rotateZ(0);
        }

        100% {
            transform: rotateZ(360deg);
        }
    }

    .bk-ip-selector {
        display: block;
    }

    .bk-ip-selector-number {
        font-weight: bold;
        color: #3a84ff;
    }

    .bk-ip-selector-number-error {
        font-weight: bold;
        color: #ea3636;
    }

    .bk-ip-selector-number-success {
        font-weight: bold;
        color: #2dcb56;
    }

    .bk-ip-selector-rotate-loading {
        display: flex;
        width: 20px;
        height: 20px;
        color: #3a84ff;
        align-items: center;
        justify-content: center;
        animation: bk-ip-selector-rotate-loading 1s linear infinite;
    }
</style>
