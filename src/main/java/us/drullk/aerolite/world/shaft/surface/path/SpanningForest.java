package us.drullk.aerolite.world.shaft.surface.path;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.util.RandomSource;

import java.util.Arrays;

/**
 * A uniform spanning forest, one tree per component, sampled with Wilson's algorithm.
 *
 * <p>The root of each component is its first node in the node list. The walks start at each node in the node list.
 * Each step of a walk takes one edge of its node with one {@code nextInt} draw.
 *
 * <p>Written by Claude Code; conversation transcript: {@code llm/transcripts/transcript_2026-11-03_21-30_59c3cf2d-0f5c-441f-8955-6758c09560e0.md}.
 */
public final class SpanningForest {

    private SpanningForest() {
    }

    /**
     * Samples the forest.
     *
     * @param nodes the nodes, sorted and distinct
     * @param us the first end of each edge, a node of the list
     * @param vs the second end of each edge, a node of the list; an edge with equal ends is a loop and never joins the forest
     * @return the indices of the forest edges
     */
    public static IntSet sample(LongList nodes, LongList us, LongList vs, RandomSource random) {
        int n = nodes.size();
        long[] sorted = nodes.toLongArray();
        IntList[] edgeAt = new IntList[n];
        IntList[] otherAt = new IntList[n];
        for (int i = 0; i < n; i++) {
            edgeAt[i] = new IntArrayList(4);
            otherAt[i] = new IntArrayList(4);
        }
        for (int e = 0; e < us.size(); e++) {
            int a = indexOf(sorted, us.getLong(e));
            int b = indexOf(sorted, vs.getLong(e));
            if (a != b) {
                edgeAt[a].add(e);
                otherAt[a].add(b);
                edgeAt[b].add(e);
                otherAt[b].add(a);
            }
        }
        boolean[] inTree = new boolean[n];
        boolean[] seen = new boolean[n];
        IntArrayList stack = new IntArrayList();
        for (int root = 0; root < n; root++) {
            if (seen[root]) {
                continue;
            }
            seen[root] = true;
            stack.add(root);
            while (!stack.isEmpty()) {
                int q = stack.popInt();
                for (int i = 0; i < otherAt[q].size(); i++) {
                    int w = otherAt[q].getInt(i);
                    if (!seen[w]) {
                        seen[w] = true;
                        stack.add(w);
                    }
                }
            }
            inTree[root] = true;
        }
        IntSet tree = new IntOpenHashSet();
        int[] nextEdge = new int[n];
        int[] nextNode = new int[n];
        for (int start = 0; start < n; start++) {
            int u = start;
            while (!inTree[u]) {
                int pick = random.nextInt(edgeAt[u].size());
                nextEdge[u] = edgeAt[u].getInt(pick);
                nextNode[u] = otherAt[u].getInt(pick);
                u = nextNode[u];
            }
            // The last exit from each node overwrites its step, so the walk loses its loops.
            u = start;
            while (!inTree[u]) {
                inTree[u] = true;
                tree.add(nextEdge[u]);
                u = nextNode[u];
            }
        }
        return tree;
    }

    private static int indexOf(long[] sorted, long node) {
        int i = Arrays.binarySearch(sorted, node);
        if (i < 0) {
            throw new IllegalArgumentException("Edge end " + BandPos.toString(node) + " is not a node");
        }
        return i;
    }

}
