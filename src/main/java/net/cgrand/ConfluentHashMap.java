package net.cgrand;

import java.util.Iterator;
import java.util.Map;

import clojure.lang.APersistentMap;
import clojure.lang.ASeq;
import clojure.lang.ATransientMap;
import clojure.lang.IEditableCollection;
import clojure.lang.IFn;
import clojure.lang.IMapEntry;
import clojure.lang.IPersistentCollection;
import clojure.lang.IPersistentMap;
import clojure.lang.IPersistentVector;
import clojure.lang.ISeq;
import clojure.lang.ITransientCollection;
import clojure.lang.ITransientMap;
import clojure.lang.MapEntry;
import clojure.lang.Obj;
import clojure.lang.RT;
import clojure.lang.SeqIterator;
import clojure.lang.Util;


public class ConfluentHashMap extends APersistentMap implements IEditableCollection {
    static private final class Node {
        static class Seq extends ASeq {
            final long bitmap;
            final Object[] array;
            final ISeq nexts;
            final int lvl;
            
            private static ISeq create(long bitmap, Object[] array, ISeq nexts, int lvl) {
                while (bitmap != 0L) {
                    switch ((int) (bitmap & 3L)) {
                    case 0: 
                        bitmap >>>= Long.numberOfTrailingZeros(bitmap) & -2;
                        break;
                    case 3:
                        return new Seq(null, bitmap >>> 2, array, nexts, lvl);
                    default:
                        Object object = array[Long.bitCount(bitmap) - 1];
                        nexts = lvl == 30 ? Collisions.Seq.create(object, nexts) : Node.Seq.create(object, nexts, lvl+5);
                        bitmap >>>= 2;
                    }
                }
                return nexts;
            }

            static ISeq create(Object node_, ISeq nexts, int lvl) {
                Node child = (Node) node_;
                return create(child.bitmap, child.array, nexts, lvl);
            }
            
            private Seq(IPersistentMap meta, long bitmap, Object[] array, ISeq nexts, int lvl) {
                super(meta);
                this.bitmap = bitmap;
                this.array = array;
                this.nexts = nexts;
                this.lvl = lvl;
            }
        
            public Object first() {
                int pos = Long.bitCount(bitmap);
                return new MapEntry(array[pos], array[pos+1]);
            }
        
            public ISeq next() {
                return create(bitmap, array, nexts, lvl);
            }
        
            public Obj withMeta(IPersistentMap meta) {
                return new Seq(meta, bitmap, array, nexts, lvl);
            }
            
        }

        private long bitmap;
        private Object array[];
        private int count;

        Node(long bitmap, Object[] array, int count) {
            this.bitmap = bitmap;
            this.array = array;
            this.count = count;
        }
        
        static final Node EMPTY = new Node(0L, new Object[0], 0);

        static Node assoc(Node node, int hash, Object key, Object val, int lvl) {
            int shift = ((hash >>> lvl) & 31)*2;
            long lead = node.bitmap >>> shift;
            long bits = lead & 3L;
            int pos = Long.bitCount(lead);
            switch ((int) bits) {
            case 0:
                return Node.copyAndInsert(node, shift, pos, key, val);
            case 3:
                Object k = node.array[pos-2];
                Object v = node.array[pos-1];
                if (Util.equiv(key, k))
                    return val != v ? Node.copyAndSet(node, pos-1, val, node.count) : node;
                return Node.copyAndPromote(node, shift, pos, pushdown(lvl+5, hash, key, val, Util.hasheq(k), k, v));
            default:
                Object child = node.array[pos-1];
                if (lvl == 30) {
                    Collisions collisions = (Collisions) child;
                    Collisions newcollisions = Collisions.extend(collisions, key, val);
                    if (newcollisions == collisions) return node;
                    return Node.copyAndSet(node, pos-1, newcollisions, node.count+newcollisions.count-collisions.count);
                } else {
                    Node childnode = (Node) child;
                    Node newchild = assoc(childnode, hash, key, val, lvl+5);
                    if (childnode == newchild) return node;
                    return Node.copyAndSet(node, pos-1, newchild, node.count+newchild.count-childnode.count);                    
                }
            }
        }

        // transient assoc, shared flag to indicate when the node is not yet owned by the transient
        static Node assoc(Node node, int hash, Object key, Object val, int lvl, boolean shared) {
            int shift = ((hash >>> lvl) & 31)*2;
            long lead = node.bitmap >>> shift;
            long bits = lead & 3L;
            int pos = Long.bitCount(lead);
            switch ((int) bits) {
            case 0:
                return shared ? flush(Node.copyAndInsert(node, shift, pos, key, val)) : Node.insert(node, shift, pos, key, val);
            case 3:
                Object k = node.array[pos-2];
                Object v = node.array[pos-1];
                if (Util.equiv(key, k))
                    return val != v ? Node.copyAndSet(node, pos-1, val, node.count) : node;
                return Node.copyAndPromote(node, shift, pos, pushdown(lvl+5, hash, key, val, Util.hasheq(k), k, v));
            default:
                Object child = node.array[pos-1];
                if (lvl == 30) {
                    Collisions collisions = (Collisions) child;
                    Collisions newcollisions = Collisions.extend(collisions, key, val);
                    if (newcollisions == collisions) return node;
                    return Node.copyAndSet(node, pos-1, newcollisions, node.count+newcollisions.count-collisions.count);
                } else {
                    Node childnode = (Node) child;
                    int cnt = childnode.count;
                    Node newchild = assoc(childnode, hash, key, val, lvl+5, shared || (bits == 1L));
                    if (shared && childnode == newchild) return node;
                    cnt = node.count+newchild.count-cnt;
                    Node newNode = shared ? flush(Node.copyAndSet(node, pos-1, newchild, cnt)) 
                            : Node.set(node, pos-1, newchild, cnt);
                    if (bits == 1L) newNode.bitmap ^= 3L << shift;  
                    return newNode;                    
                }
            }
        }

        static Node dissoc(Node node, int hash, Object key, int lvl) {
            int shift = ((hash >>> lvl) & 31)*2;
            long lead = node.bitmap >>> shift;
            long bits = lead & 3L;
            int pos = Long.bitCount(lead);
            switch ((int) bits) {
            case 0:
                return node;
            case 3:
                Object k = node.array[pos-2];
                if (Util.equiv(key, k)) 
                    return copyAndRemoveKV(node, shift, pos-2);
                return node;
            default:
                Object child = node.array[pos-1];
                return dissocMayCollapse((Node) child, hash, key, lvl+5, node, shift, pos);
            }
        }

        static Node copyAndDemote(Node node, int shift, int pos, Object k, Object v) {
            long bitmap = node.bitmap | (3L << shift); // set to 3
            int len = Long.bitCount(bitmap);
            Object[] array = new Object[len];
            System.arraycopy(node.array, 0, array, 0, pos);
            array[pos] = k;
            array[pos+1] = v;
            System.arraycopy(node.array, pos+1, array, pos+2, len-(pos+2));
            return new Node(bitmap, array, node.count-1);
        }

        static Node copyAndInsert(Node node, int shift, int pos, Object key, Object val) {
            long bitmap = node.bitmap | (3L << shift);
            int len = Long.bitCount(bitmap);
            Object[] array = new Object[len];
            System.arraycopy(node.array, 0, array, 0, pos);
            array[pos] = key;
            array[pos+1] = val;
            System.arraycopy(node.array, pos, array, pos+2, len-pos-2);
            return new Node(bitmap, array, node.count+1);
        }

        static Node insert(Node node, int shift, int pos, Object key, Object val) {
            long bitmap = node.bitmap | (3L << shift);
            int len = Long.bitCount(bitmap);
            node.bitmap = bitmap;
            node.count++;
            Object[] array = node.array;
            if (node.array.length < len) {
                node.array = new Object[len + 2];
                System.arraycopy(array, 0, node.array, 0, pos);
            }
            System.arraycopy(array, pos, node.array, pos+2, len-pos-2);
            node.array[pos] = key;
            node.array[pos+1] = val;
            return node;
        }

        static Node copyAndPromote(Node node, int shift, int pos, Object x) {
            long bitmap = node.bitmap ^ (1L << shift); // set to 2
            int len = Long.bitCount(bitmap);
            Object[] array = new Object[len];
            System.arraycopy(node.array, 0, array, 0, pos-2);
            array[pos-2] = x;
            System.arraycopy(node.array, pos, array, pos-1, len-pos+1);
            return new Node(bitmap, array, node.count+1);
        }

        static Node copyAndRemoveKV(Node node, int shift, int pos) {
            long bitmap = node.bitmap ^ (3L << shift); // set to 2
            int len = Long.bitCount(bitmap);
            Object[] array = new Object[len];
            System.arraycopy(node.array, 0, array, 0, pos);
            System.arraycopy(node.array, pos+2, array, pos, len-pos);
            return new Node(bitmap, array, node.count-1);
        }

        static Node copyAndSet(Node node, int i, Object v, int count) {
            return new Node(node.bitmap, aset(node.array, i, v), count);
        }

        static Node set(Node node, int i, Object v, int count) {
            node.count = count;
            node.array[i] = v;
            return node;
        }

        static Node dissocMayCollapse(Node node, int hash, Object key, int lvl, Node parent, int pshift, int ppos) {
            int shift = ((hash >>> lvl) & 31)*2;
            long lead = node.bitmap >>> shift;
            long bits = lead & 3L;
            int pos = Long.bitCount(lead);
            switch ((int) bits) {
            case 0:
                return parent;
            case 3:
                Object k = node.array[pos-2];
                if (Util.equiv(key, k))
                    if (node.count > 2)
                        return copyAndSet(parent, ppos-1, copyAndRemoveKV(node, shift, pos-2), parent.count-1);
                    else {
                        pos = (pos - 2)^2;
                        return copyAndDemote(parent, pshift, ppos-1, node.array[pos], node.array[pos+1]);                    
                    }
                return parent;
            default:
                Object child = node.array[pos-1];
                if (node.count > 2) {
                    Node newnode = lvl == 30 ? Collisions.dissoc(node, shift, pos, (Collisions) child, key) : 
                        dissocMayCollapse((Node) child, hash, key, lvl+5, node, shift, pos);
                    if (newnode == node) return parent;
                    return copyAndSet(parent, ppos-1, newnode, parent.count-1);
                }
                return lvl == 30 ? Collisions.dissoc(node, shift, pos, (Collisions) child, key) : dissocMayCollapse((Node) child, hash, key, lvl+5, parent, pshift, ppos);
            }
        }
        
        /**
         * Mutates node to set all children in "shared" state (01)
         */
        static Node flush(Node node) {
            long x = node.bitmap - ((-6148914691236517206L & node.bitmap) >>> 1); // 11 -> 10, 10 -> 01, _ -> _
            node.bitmap = x | ((-6148914691236517206L & x) >>> 1); // 10 -> 11, _ -> _
            return node;
        }
        
        static Node merge(Node anc, Node a, Node b, IFn fix, Object notFound, int lvl) {
            // never, ever, ever return anc as anc is shared
            if (anc == a) return b; // also handles when anc and a are EMPTY
            if (anc == b || a == b) return a; // also handles when anc and b are EMPTY or when there's a closest common ancestor
            
            // fix is never called!?
            Object[] array = new Object[64];
            long bitmap = 0;
            int i = array.length;
            int count = 0;
            long bmab =  ((a.bitmap | a.bitmap >>> 1 | b.bitmap | b.bitmap >>> 1) & 6148914691236517205L);
            for(int shift = Long.numberOfTrailingZeros(bmab); shift < 64; shift += 2 + Long.numberOfTrailingZeros(bmab >>> (shift+2))) {
                switch((int) (((a.bitmap >>> shift) & 3L) << 2 | ((b.bitmap >>> shift) & 3L))) {
                case 3: { // no a, kv b
                    int bi = Long.bitCount(b.bitmap >>> shift);
                    Object kb = b.array[bi-2],
                    vb = b.array[bi-1],
                    vanc = lookup(anc, kb, notFound, lvl),
                    vr = vanc == notFound ? vb : vanc == vb ? notFound : fix.invoke(kb, vanc, notFound, vb, notFound);
                    if (vr != notFound) {
                        bitmap |= 3L << shift;
                        count++;
                        array[--i] = vr;
                        array[--i] = kb;
                    }
                    continue;
                }
                case 12: { // kv a, no b
                    int ai = Long.bitCount(a.bitmap >>> shift);
                    Object ka = a.array[ai-2],
                    va = a.array[ai-1],
                    vanc = lookup(anc, ka, notFound, lvl),
                    vr = vanc == notFound ? va : vanc == va ? notFound : fix.invoke(ka, vanc, va, notFound, notFound);
                    if (vr != notFound) {
                        bitmap |= 3L << shift;
                        count++;
                        array[--i] = vr;
                        array[--i] = ka;
                    }
                    continue;
                }
                case 15: { // kv a, kv b
                    int ai = Long.bitCount(a.bitmap >>> shift);
                    Object ka = a.array[ai-2],
                    va = a.array[ai-1];
                    int bi = Long.bitCount(b.bitmap >>> shift);
                    Object kb = b.array[bi-2],
                    vb = b.array[bi-1];
                    if (!Util.equiv(ka, kb)) break;
                    Object vr;
                    if (va == vb) {
                        vr = vb;                      
                    } else {
                        Object vanc = lookup(anc, ka, notFound, lvl);
                        vr = vanc == va ? vb : vanc == vb ? va : fix.invoke(ka, vanc, va, vb, notFound);  
                    }
                    if (vr != notFound) {
                        bitmap |= 3L << shift;
                        count++;
                        array[--i] = vr;
                        array[--i] = kb;
                    }
                    continue;
                }
                }
                if (lvl < 30) {
                    Node nanc = node(anc.array, anc.bitmap, shift, lvl);
                    Node na = node(a.array, a.bitmap, shift, lvl);
                    Node nb = node(b.array, b.bitmap, shift, lvl);
                    Node r = merge(nanc, na, nb, fix, notFound, lvl + 5);
                    if (r == null) continue;
                    count += r.count;
                    if (r.count == 1) {
                        bitmap |= 3L << shift;
                        array[--i] = r.array[1];
                        array[--i] = r.array[0];
                        continue;
                    }
                    array[--i] = r;
                    bitmap |= (r == na) ? a.bitmap & (3L << shift)
                            : (r == nb) ? b.bitmap & (3L << shift)
                                    : 2L << shift;
                } else {
                    Collisions nanc = collisions(anc.array, anc.bitmap, shift);
                    Collisions na = collisions(a.array, a.bitmap, shift);
                    Collisions nb = collisions(b.array, b.bitmap, shift);
                    Collisions r = Collisions.merge(nanc, na, nb, fix, notFound);
                    if (r == null) continue;
                    count += r.count;
                    if (r.count == 1) {
                        bitmap |= 3L << shift;
                        array[--i] = r.array[1];
                        array[--i] = r.array[0];
                        continue;
                    }
                    array[--i] = r;
                    bitmap |= (r == na) ? a.bitmap & (3L << shift)
                            : (r == nb) ? b.bitmap & (3L << shift)
                                    : 2L << shift;
                }
            }
            if (count == 0) return null;
            Object[] ra = new Object[Long.bitCount(bitmap)];
            System.arraycopy(array, i, ra, 0, array.length - i);
            return new Node(bitmap, ra, count);
        }

        private static Node node(Object[] array, long bitmap, int shift, int lvl) {
            switch((int) ((bitmap >>> shift) & 3L)) {
            case 0: return EMPTY;
            case 1: case 2: return (Node) array[Long.bitCount(bitmap >>> shift)-1];
            default:
               int pos = Long.bitCount(bitmap >>> shift);
                Object k = array[pos-2];
                Object v = array[pos-1];
                return new Node(3L << ((Util.hasheq(k) >>> (lvl + 5)) & 31), new Object[] {k, v}, 1);
            }
        }

        private static Collisions collisions(Object[] array, long bitmap, int shift) {
            switch((int) ((bitmap >>> shift) & 3L)) {
            case 0: return Collisions.EMPTY;
            case 1: case 2: return (Collisions) array[Long.bitCount(bitmap >>> shift)-1];
            default:
               int pos = Long.bitCount(bitmap >>> shift);
                Object k = array[pos-2];
                Object v = array[pos-1];
                return new Collisions(Util.hasheq(k), new Object[] {k, v}, 1);
            }
        }

        static Object lookup(Node node, Object key, Object notFound, int lvl) {
            int h = Util.hasheq(key);
            loop: for (;;) {
                int shift = ((h >>> lvl) & 31)*2;
                long lead = node.bitmap >>> shift;
                int pos = Long.bitCount(lead);
                int bits = (int) (lead & 3L);
                switch (bits) {
                case 0:
                    return notFound;
                case 3:
                    return Util.equiv(key, node.array[pos-2]) ? node.array[pos-1] : notFound;
                default:
                    lvl+=5;
                    if (lvl >= 32) return Collisions.lookup(node.array[pos-1], key, notFound);
                    node = (Node) node.array[pos-1];
                    continue loop;
                }
            }
        }
    }
    
    static private final class Collisions {
        
        static final Collisions EMPTY = new Collisions(0, new Object[0], 0);
        
        static class Seq extends ASeq {
            final Object[] array;
            final int idx;
            final ISeq nexts;

            static ISeq create(Object collisions_, ISeq nexts) {
                Collisions child = (Collisions) collisions_;
                return Collisions.Seq.create(child.array, (child.count-1)*2, nexts);
            }
            
            private static ISeq create(Object[] array, int idx, ISeq nexts) {
                if (idx < 0) return nexts;
                return new Seq(null, array, idx, nexts);
            }
            
            private Seq(IPersistentMap meta, Object[] array, int idx, ISeq nexts) {
                super(meta);
                this.array = array;
                this.idx = idx;
                this.nexts = nexts;
            }

            public Object first() {
                return new MapEntry(array[idx], array[idx+1]);
            }

            public ISeq next() {
                return create(array, idx-2, nexts);
            }

            public Obj withMeta(IPersistentMap meta) {
                return new Seq(meta, array, idx, nexts);
            }
            
        }
        
        int count;
        int hash;
        Object array[];
        
        Collisions(int hash, Object[] array, int count) {
            this.count = count;
            this.hash = hash;
            this.array = array;
        }

        static int indexOf(Collisions collisions, Object key) {
            int i = 2*(collisions.count - 1);
            for(; i >= 0; i-=2) 
                if (Util.equiv(collisions.array[i], key)) return i;
            return i; // -2
        }

        static Collisions extend(Collisions collisions, Object key, Object val) {
            int idx = Collisions.indexOf(collisions, key);
            if (idx >= 0) {
                if (collisions.array[idx+1] == val)
                    return collisions;
                return new Collisions(collisions.hash, aset(collisions.array, idx+1, val), collisions.count);            
            }
            int len = 2*collisions.count;
            int count = collisions.count + 1;
            Object[] array = new Object[2*count];
            System.arraycopy(collisions.array, 0, array, 0, len);
            array[len] = key;
            array[len+1] = val;
            return new Collisions(collisions.hash, array, count);
        }

        static Node dissoc(Node node, int shift, int pos, Collisions collisions, Object key) {
            int idx = Collisions.indexOf(collisions, key);
            if (idx < 0) return node;
            int count = collisions.count - 1;
            if (count == 1) {
                idx ^= 2; // the remaining index
                return Node.copyAndDemote(node, shift, pos, collisions.array[idx], collisions.array[idx+1]);
            }
            int len = 2*count;
            Object[] array = new Object[len];
            System.arraycopy(collisions.array, 0, array, 0, len);
            array[idx] = collisions.array[len];
            array[idx+1] = collisions.array[len+1];
            return Node.copyAndSet(node, pos, new Collisions(collisions.hash, array, count), node.count-1);
        }

        static Object lookup(Object collisions_, Object key, Object notFound) {
            Collisions collisions = (Collisions) collisions_;
            int i = indexOf(collisions, key);
            if (i < 0) return notFound;
            return collisions.array[i+1];
        }

        static IMapEntry lookup(Object collisions_, Object key) {
            Collisions collisions = (Collisions) collisions_;
            int i = indexOf(collisions, key);
            if (i < 0) return null;
            return new MapEntry(collisions.array[i], collisions.array[i+1]);
        }
        
        static Collisions merge(Collisions anc, Collisions a, Collisions b, IFn fix, Object notFound) {
            int len = (a.count + b.count)*2;
            Object[] array = new Object[len];
            int lim = 2*a.count;
            System.arraycopy(a.array, 0, array, 0, lim);
            System.arraycopy(b.array, 0, array, lim, len - lim);
            int i = 0;
            while(i < lim) {
                Object ka = array[i];
                Object va = array[i+1];
                int j = lim;
                while((j < len) && !Util.equiv(ka, array[j])) j+=2;
                Object vb = notFound;
                if (j < len) {
                    vb = array[j+1];
                    array[j+1] = array[--len];
                    array[j] = array[--len];
                }
                
                if (va == vb) { i+=2; continue; }
                
                Object vanc = lookup(anc, ka, notFound);
                if (vanc == vb) { i+=2; continue; }
                    
                Object vr = fix.invoke(ka, vanc, va, vb, notFound);
                if (vr != notFound) {
                    array[i+1] = vr;
                    i+=2;
                } else { // deletion
                    array[i+1] = array[--lim];
                    array[lim] = array[--len];
                    array[i] = array[--lim];
                    array[lim] = array[--len];
                }
            }
            // a is exhausted
            while(i < len) {
                Object kb = array[i];
                Object vb = array[i+1];
                Object vanc = lookup(anc, kb, notFound);
                if (vanc == notFound) { i+=2; continue; }
                    
                Object vr = fix.invoke(kb, vanc, notFound, vb, notFound);
                if (vr != notFound) {
                    array[i+1] = vr;
                    i+=2;
                } else { // deletion
                    array[i+1] = array[--len];
                    array[i] = array[--len];
                }
            }
            if (i == 0) return null;
            Object ra[] = new Object[i];
            System.arraycopy(array, 0, ra, 0, i);
            int h = a.count > 0 ? a.hash : b.hash; // a and b should never be simultaneously empty
            return new Collisions(h, ra, i / 2);
        }
    }
    
    private Node root;
    
    public ConfluentHashMap(Node root) {
        this.root = root;
    }
    
    public static final ConfluentHashMap EMPTY = new ConfluentHashMap(Node.EMPTY);

    static Object pushdown(int lvl, int hash, Object key, Object val, int h, Object k, Object v) {
        if (lvl < 32) {
            int idx = (hash >>> lvl) & 31;
            int i = (h >>> lvl) & 31;
            if (i == idx) return new Node(1L << (2 * idx), new Object[] { pushdown(lvl+5, hash, key, val, h, k, v) }, 2); // could be a loop
            return new Node(3L << (2 * idx) | 3L << (2 * i), idx < i ? new Object[] { k, v, key, val } : new Object[] { key, val, k, v }, 2);
        } else { // collisions only exist at the max depth
            return new Collisions(hash, new Object[] {k, v, key, val}, 2);
        }
    }

    static Object[] aset(Object[] array, int i, Object v) {
        Object[] a = array.clone();
        a[i] = v;
        return a;
    }

    public IPersistentMap assoc(Object key, Object val) {
        Node newroot = Node.assoc(root, Util.hasheq(key), key, val, 0);
        if (newroot == root) return this;
        return new ConfluentHashMap(newroot);
    }    

    public IPersistentMap assocEx(Object key, Object val) {
        Node newroot = Node.assoc(root, Util.hasheq(key), key, val, 0);
        if (newroot.count == root.count)
            throw Util.runtimeException("Key already present");
        return new ConfluentHashMap(newroot);
    }

    public IPersistentMap without(Object key) {
        Node newroot = Node.dissoc(root, Util.hasheq(key), key, 0);
        if (newroot == root) return this;
        return new ConfluentHashMap(newroot);
    }

    public static ConfluentHashMap merge(ConfluentHashMap ancestor, ConfluentHashMap a, ConfluentHashMap b, IFn fix) {
        Node root = Node.merge(ancestor.root, a.root, b.root, fix, new Object(), 0);
        if (root == null) return EMPTY;
        return new ConfluentHashMap(root);
    }
    
    public Iterator iterator() {
        // TODO gross
        return new SeqIterator(seq());
    }

    public boolean containsKey(Object key) {
        return valAt(key, root) != root;
    }

    public IMapEntry entryAt(Object key) {
        int hash = Util.hasheq(key);
        int h = hash;
        int hops = 7;
        Node node = root;
        loop: for (;;) {
            int shift = (h & 31)*2;
            long lead = node.bitmap >>> shift;
            int pos = Long.bitCount(lead);
            int bits = (int) (lead & 3L);
            switch (bits) {
            case 0:
                return null;
            case 3:
                Object k = node.array[pos-2];
                return Util.equiv(key, k) ? new MapEntry(k, node.array[pos-1]) : null;
            default:
                hops--;
                if (hops == 0) return Collisions.lookup(node.array[pos-1], key);
                h >>>= 5;
                node = (Node) node.array[pos-1];
                continue loop;
            }
        }
    }

    public int count() {
        return root.count;
    }

    public IPersistentCollection empty() {
        // TODO support meta
        return new ConfluentHashMap(Node.EMPTY);
    }

    public ISeq seq() {
        return Node.Seq.create(root, null, 0);
    }

    public Object valAt(Object key) {
        return valAt(key, null);
    }

    public Object valAt(Object key, Object notFound) {
        return Node.lookup(root, key, notFound, 0);
    }

    public ITransientCollection asTransient() {
        return new Transient(root);
    }
    
    static class Transient implements ITransientMap {
        private volatile boolean editable;
        private Node root;
        private boolean sharedRoot;

        // inline APersistentMap because of visibility issues
        public ITransientMap conj(Object o) {
            ensureEditable();
            if(o instanceof Map.Entry)
                {
                Map.Entry e = (Map.Entry) o;
            
                return assoc(e.getKey(), e.getValue());
                }
            else if(o instanceof IPersistentVector)
                {
                IPersistentVector v = (IPersistentVector) o;
                if(v.count() != 2)
                    throw new IllegalArgumentException("Vector arg to map conj must be a pair");
                return assoc(v.nth(0), v.nth(1));
                }
            
            ITransientMap ret = this;
            for(ISeq es = RT.seq(o); es != null; es = es.next())
                {
                Map.Entry e = (Map.Entry) es.first();
                ret = ret.assoc(e.getKey(), e.getValue());
                }
            return ret;
        }

        public final Object invoke(Object arg1) {
            return valAt(arg1);
        }

        public final Object invoke(Object arg1, Object notFound) {
            return valAt(arg1, notFound);
        }

        public final Object valAt(Object key) {
            return valAt(key, null);
        }

        public final ITransientMap assoc(Object key, Object val) {
            ensureEditable();
            return doAssoc(key, val);
        }

        public final ITransientMap without(Object key) {
            ensureEditable();
            return doWithout(key);
        }

        public final IPersistentMap persistent() {
            ensureEditable();
            return doPersistent();
        }

        public final Object valAt(Object key, Object notFound) {
            ensureEditable();
            return doValAt(key, notFound);
        }

        public final int count() {
            ensureEditable();
            return doCount();
        }
        // end APersitentMap
        
        public Transient(Node root) {
            editable = true;
            sharedRoot = true;
            this.root = root;
        }

        void ensureEditable() {
            if (!editable)
                throw new IllegalAccessError("Transient used after persistent! call");
        }

        ITransientMap doAssoc(Object key, Object val) {
            Node newRoot = Node.assoc(root, Util.hasheq(key), key, val, 0, sharedRoot);
            if (newRoot != root) {
                sharedRoot = false;
                root = newRoot;
            }
            return this;
        }

        ITransientMap doWithout(Object key) {
            // TODO Auto-generated method stub
            return null;
        }

        Object doValAt(Object key, Object notFound) {
            return Node.lookup(root, key, notFound, 0);
        }

        int doCount() {
            return root.count;
        }

        IPersistentMap doPersistent() {
            editable = false;
            return new ConfluentHashMap(root);
        }
    }
}
