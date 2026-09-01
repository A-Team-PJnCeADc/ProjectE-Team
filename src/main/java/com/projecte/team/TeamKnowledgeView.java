package com.projecte.team;

import java.util.AbstractList;
import java.util.Iterator;

import net.minecraft.item.ItemStack;

/**
 * 团队知识列表的活视图:对列表的增删改操作(ProjectE 的 Transmutation 直接对
 * getKnowledge() 返回的列表执行 add/remove/clear/addAll)会被本视图拦截并立即
 * 标记脏数据,由 TeamSaveHandler 在下一个服务器 tick 内写盘(即时持久化)。
 */
public class TeamKnowledgeView extends AbstractList<ItemStack> {

    private final TeamData team;

    public TeamKnowledgeView(TeamData team) {
        this.team = team;
    }

    @Override
    public ItemStack get(int index) {
        return team.getKnowledge()
            .get(index);
    }

    @Override
    public int size() {
        return team.getKnowledge()
            .size();
    }

    @Override
    public ItemStack set(int index, ItemStack element) {
        ItemStack old = team.getKnowledge()
            .set(index, element);
        TeamManager.markDirty();
        return old;
    }

    @Override
    public void add(int index, ItemStack element) {
        team.getKnowledge()
            .add(index, element);
        team.pruneDuplicateKnowledge();
        TeamManager.markDirty();
    }

    @Override
    public ItemStack remove(int index) {
        ItemStack old = team.getKnowledge()
            .remove(index);
        TeamManager.markDirty();
        return old;
    }

    @Override
    public void clear() {
        team.getKnowledge()
            .clear();
        TeamManager.markDirty();
    }

    @Override
    public Iterator<ItemStack> iterator() {
        return new ViewIterator();
    }

    /**
     * 包装底层迭代器,使 remove()(ProjectE 的 removeKnowledge 使用)也能标记脏数据。
     * remove(Object)/addAll(Collection) 基于 iterator 或 add 实现,自动走本类的覆写。
     */
    private class ViewIterator implements Iterator<ItemStack> {

        private final Iterator<ItemStack> backing = team.getKnowledge()
            .iterator();
        private boolean canRemove = false;

        @Override
        public boolean hasNext() {
            return backing.hasNext();
        }

        @Override
        public ItemStack next() {
            canRemove = true;
            return backing.next();
        }

        @Override
        public void remove() {
            if (!canRemove) {
                throw new IllegalStateException();
            }
            backing.remove();
            canRemove = false;
            TeamManager.markDirty();
        }
    }
}
