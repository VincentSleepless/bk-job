<template>
    <div class="ip-selector-static-topo">
        <resize-layout
            v-if="topoTreeData.length > 0"
            :default-width="265"
            flex-direction="left">
            <div class="tree-box">
                <bk-input
                    v-model="filterKey"
                    clearable
                    placeholder="搜索拓扑节点"
                    style="margin-bottom: 12px;" />
                <div class="big-tree-box">
                    <bk-big-tree
                        ref="treeRef"
                        :data="topoTreeData"
                        :expand-on-click="false"
                        :filter-method="filterMethod"
                        :lazy-disabled="lazyDisabledCallbak"
                        :lazy-method="lazyMethodCallback"
                        selectable
                        show-link-line
                        @node-click="handleTreeClick"
                        @select-change="handleNodeSelect">
                        <template #default="{ node: nodeItem, data }">
                            <div class="topo-node-box">
                                <div class="topo-node-name">
                                    {{ data.name }}
                                </div>
                                <template v-if="nodeItem.level === 0">
                                    <div
                                        :key="`filter_${isHidedEmptyNode}`"
                                        v-bk-tooltips="`${isHidedEmptyNode ? '显示没有主机的节点' : '隐藏没有主机的节点' }`"
                                        class="topo-node-filter"
                                        :style="{
                                            opacity: isHidedEmptyNode ? 1 : 0
                                        }"
                                        @click.stop="handleToggleFilterWithCount">
                                        <ip-selector-icon :type="`${isHidedEmptyNode ? 'invisible1' : 'visible1'}`" />
                                    </div>
                                </template>
                                <div
                                    v-if="calcShowExpanded(nodeItem)"
                                    :key="`expanded_${nodeItem.expanded}`"
                                    v-bk-tooltips="`${nodeItem.expanded ? '收起所有节点' : '展开所有节点'}`"
                                    class="topo-node-expand"
                                    @click.stop="handleToggleTopoTreeExpanded(nodeItem)">
                                    <ip-selector-icon :type="`${nodeItem.expanded ? 'shangxiachengkai-2' : 'shangxiachengkai'}`" />
                                </div>
                                <div
                                    class="topo-node-count"
                                    :class="{
                                        'is-selected': nodeItem.selected
                                    }">
                                    {{ data.payload.count }}
                                </div>
                            </div>
                        </template>
                    </bk-big-tree>
                </div>
            </div>
            <template #right>
                <div
                    v-bkloading="{ isLoading: isHostLoading }"
                    class="host-table">
                    <bk-input
                        v-model="nodeHostListSearch"
                        clearable
                        placeholder="请输入 IP/IPv6/主机名称/OS 名称"
                        style="margin-bottom: 12px;"
                        @clear="handleHostListSearchClear"
                        @keyup="handleEnterKeyUp" />
                    <render-host-table
                        :data="hostTableData"
                        :height="renderTableHeight"
                        :pagination="pagination"
                        @pagination-change="handlePaginationChange"
                        @row-click="handleRowClick">
                        <template #header-selection>
                            <table-page-check
                                :disabled="hostTableData.length < 1"
                                :value="pageCheckValue"
                                @change="handlePageCheck" />
                        </template>
                        <template #selection="{ row }">
                            <span v-bk-tooltips="checkHostDisable(row).tooltips">
                                <bk-checkbox
                                    :disabled="checkHostDisable(row).disabled"
                                    :value="Boolean(hostCheckedMap[row.host_id])" />
                            </span>
                        </template>
                    </render-host-table>
                </div>
            </template>
        </resize-layout>
        <div
            v-else
            v-bkloading="{ isLoading: isConfigLoading }"
            class="create-static-topo">
            <span>无数据，</span>
            <a
                :href="config.bk_cmdb_static_topo_url"
                target="_blank">
                去创建
            </a>
        </div>
    </div>
</template>
<script>
    export default {
        inheritAttrs: false,
    };
</script>
<script setup>
    import {
        nextTick,
        reactive,
        ref,
        shallowRef,
        watch,
    } from 'vue';

    import IpSelectorIcon from '../../../common/ip-selector-icon';
    import RenderHostTable from '../../../common/render-table/host/index.vue';
    import useDebounceRef from '../../../hooks/use-debounced-ref';
    import useDialogSize from '../../../hooks/use-dialog-size';
    import useFetchConfig from '../../../hooks/use-fetch-config';
    import useHostDisable from '../../../hooks/use-host-disable';
    import useInputEnter from '../../../hooks/use-input-enter';
    import useTreeExpanded from '../../../hooks/use-tree-expanded';
    import useTreeFilter from '../../../hooks/use-tree-filter';
    import useTreeLazy from '../../../hooks/use-tree-lazy';
    import Manager from '../../../manager';
    import {
        getPaginationDefault,
     } from '../../../utils';
    import ResizeLayout from '../resize-layout.vue';
    import TablePageCheck from '../table-page-check.vue';

    const props = defineProps({
        topoTreeData: {
            type: Array,
            required: true,
        },
        lastHostList: {
            type: Array,
            required: true,
        },
    });

    const emits = defineEmits([
        'change',
    ]);

    const tableOffetTop = 60;
    const {
        contentHeight: dialogContentHeight,
    } = useDialogSize();
    const renderTableHeight = dialogContentHeight.value - tableOffetTop;

    const pagination = reactive(getPaginationDefault(renderTableHeight));

    const {
        loading: isConfigLoading,
        config,
    } = useFetchConfig();

    const treeRef = ref();
    const topoTreeSearch = useDebounceRef('');

    const isHostLoading = ref(false);
    const hostTableData = shallowRef([]);

    const pageCheckValue = ref('');
    const hostCheckedMap = shallowRef({});
    const nodeHostListSearch = ref('');

    let selectedTopoNodeData;
    let isInnerChange = false;

    const {
        filterKey,
        filterMethod,
        filterWithCount: isHidedEmptyNode,
        toggleFilterWithCount: handleToggleFilterWithCount,
    } = useTreeFilter(treeRef);

    const {
        toggleExpanded: handleToggleTopoTreeExpanded,
        calcShowExpanded,
        handleClick: handleTreeClick,
    } = useTreeExpanded(treeRef);

    const {
        lazyDisabledCallbak,
        lazyMethodCallback,
    } = useTreeLazy();

    const checkHostDisable = useHostDisable();

    // 判断 page-check 的状态
    const syncTablePageCheckValue = () => {
        setTimeout(() => {
            if (hostTableData.value.length > 0) {
                pageCheckValue.value = 'page';
                hostTableData.value.forEach((hostItem) => {
                    if (!hostCheckedMap.value[hostItem.host_id]) {
                        pageCheckValue.value = '';
                    }
                });
            } else {
                pageCheckValue.value = '';
            }
        });
    };

    // 同步拓扑树的值
    watch(() => props.topoTreeData, () => {
        nextTick(() => {
            if (props.topoTreeData.legnth < 1) {
                return;
            }
            const [rootFirstNode] = props.topoTreeData;
            treeRef.value.setSelected(rootFirstNode.id, {
                emitEvent: true,
            });
            treeRef.value.setExpanded(rootFirstNode.id);
        });
    }, {
        immediate: true,
    });

    // 同步主机的选中状态
    watch(() => props.lastHostList, (lastHostList) => {
        if (isInnerChange) {
            isInnerChange = false;
            return;
        }
        hostCheckedMap.value = lastHostList.reduce((result, hostItem) => {
            result[hostItem.host_id] = hostItem;
            return result;
        }, {});
        syncTablePageCheckValue();
    }, {
        immediate: true,
    });

    // 拓扑树搜索
    watch(topoTreeSearch, () => {
        treeRef.value.filter(topoTreeSearch.value);
    });

    const triggerChange = () => {
        isInnerChange = true;
        emits('change', 'hostList', Object.values(hostCheckedMap.value));
    };

    // 获取选中节点的主机列表
    let searchContentMemo = '';
    const fetchNodeHostList = () => {
        if (!selectedTopoNodeData) {
            return;
        }
        isHostLoading.value = true;
        Manager.service.fetchTopologyHostsNodes({
            [Manager.nameStyle('nodeList')]: [
                {
                    [Manager.nameStyle('objectId')]: selectedTopoNodeData.object_id,
                    [Manager.nameStyle('instanceId')]: selectedTopoNodeData.instance_id,
                    [Manager.nameStyle('meta')]: selectedTopoNodeData.meta,
                },
            ],
            [Manager.nameStyle('pageSize')]: pagination.limit,
            [Manager.nameStyle('start')]: (pagination.current - 1) * pagination.limit,
            [Manager.nameStyle('searchContent')]: nodeHostListSearch.value,
        }).then((data) => {
            hostTableData.value = data.data;
            pagination.count = data.total;
            searchContentMemo = nodeHostListSearch.value;
            syncTablePageCheckValue();
        })
        .finally(() => {
            isHostLoading.value = false;
        });
    };

    // enter 建触发主机搜索
    const handleEnterKeyUp = useInputEnter(() => {
        pagination.current = 1;
        fetchNodeHostList();
    });

    // 清空主机搜索
    const handleHostListSearchClear = () => {
        pagination.current = 1;
        if (searchContentMemo) {
            fetchNodeHostList();
        }
    };

    // 获取选中节点的完整主机列表
    const fetchNodeAllHostId = () => Manager.service.fetchTopologyHostIdsNodes({
        [Manager.nameStyle('nodeList')]: [
                {
                    [Manager.nameStyle('objectId')]: selectedTopoNodeData.object_id,
                    [Manager.nameStyle('instanceId')]: selectedTopoNodeData.instance_id,
                    [Manager.nameStyle('meta')]: selectedTopoNodeData.meta,
                },
            ],
            [Manager.nameStyle('searchContent')]: nodeHostListSearch.value,
        }).then(data => data.data);

    // 选中节点
    const handleNodeSelect = (node) => {
        selectedTopoNodeData = node.data.payload;
        pagination.current = 1;
        fetchNodeHostList();
    };

    // 本页全选、跨页全选
    const handlePageCheck = (checkValue) => {
        const checkedMap = { ...hostCheckedMap.value };
        Promise.resolve()
            .then(() => {
                if (checkValue === 'page') {
                    hostTableData.value.forEach((hostDataItem) => {
                        if (checkHostDisable(hostDataItem).disabled) {
                            return;
                        }
                        checkedMap[hostDataItem.host_id] = hostDataItem;
                    });
                } else if (checkValue === 'pageCancle') {
                    hostTableData.value.forEach((hostDataItem) => {
                        if (checkHostDisable(hostDataItem).disabled) {
                            return;
                        }
                        delete checkedMap[hostDataItem.host_id];
                    });
                } else if (checkValue === 'allCancle') {
                    return fetchNodeAllHostId()
                        .then((data) => {
                            data.forEach((hostDataItem) => {
                                if (checkHostDisable(hostDataItem).disabled) {
                                    return;
                                }
                                delete checkedMap[hostDataItem.host_id];
                            });
                        });
                } else if (checkValue === 'all') {
                    return fetchNodeAllHostId()
                        .then((data) => {
                            data.forEach((hostDataItem) => {
                                if (checkHostDisable(hostDataItem).disabled) {
                                    return;
                                }
                                checkedMap[hostDataItem.host_id] = hostDataItem;
                            });
                        });
                }
            })
            .then(() => {
                hostCheckedMap.value = checkedMap;
                pageCheckValue.value = checkValue;
                triggerChange();
            });
    };

    // 选中指定主机
    const handleRowClick = (data) => {
        if (checkHostDisable(data).disabled) {
            return;
        }
        const checkedMap = { ...hostCheckedMap.value };
        if (checkedMap[data.host_id]) {
            delete checkedMap[data.host_id];
        } else {
            checkedMap[data.host_id] = data;
        }
        hostCheckedMap.value = checkedMap;
        syncTablePageCheckValue();
        triggerChange();
    };

    // 分页
    const handlePaginationChange = (currentPagination) => {
        pagination.current = currentPagination.current;
        pagination.limit = currentPagination.limit;
        fetchNodeHostList();
    };
</script>
<style lang="postcss">
    @import "../../../styles/tree.mixin.css";

    .ip-selector-static-topo {
        height: 100%;

        .tree-box {
            height: 100%;
            padding-right: 16px;
            padding-left: 16px;
            overflow: auto;

            @include tree;
        }

        .host-table {
            flex: 1;
            padding-left: 24px;
        }

        .create-static-topo {
            width: 100%;
            padding-top: 120px;
            text-align: center;
        }
    }
</style>
