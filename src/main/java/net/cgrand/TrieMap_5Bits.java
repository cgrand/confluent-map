package net.cgrand;
/*******************************************************************************
 * Copyright (c) 2013-2015 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *
 *   * Michael Steindorfer - Michael.Steindorfer@cwi.nl - CWI  
 *******************************************************************************/


import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import clojure.lang.APersistentMap;
import clojure.lang.IEditableCollection;
import clojure.lang.IMapEntry;
import clojure.lang.IPersistentCollection;
import clojure.lang.IPersistentMap;
import clojure.lang.IPersistentVector;
import clojure.lang.ISeq;
import clojure.lang.ITransientCollection;
import clojure.lang.ITransientMap;
import clojure.lang.MapEntry;
import clojure.lang.RT;
import clojure.lang.Util;

@SuppressWarnings("rawtypes")
public class TrieMap_5Bits extends APersistentMap implements IEditableCollection {

    public static final MapEntry entryOf(final Object key, final Object val) {
        return new MapEntry(key, val);
    }


	@SuppressWarnings("unchecked")
	public static final TrieMap_5Bits EMPTY_MAP = new TrieMap_5Bits(CompactMapNode.EMPTY_NODE, 0);

	private static final boolean DEBUG = false;

	private final AbstractMapNode rootNode;
	private final int cachedSize;

	TrieMap_5Bits(AbstractMapNode rootNode, int cachedSize) {
		this.rootNode = rootNode;
		this.cachedSize = cachedSize;
	}

	public boolean containsKey(final Object key) {
		try {
			return rootNode.containsKey(key, Util.hasheq(key), 0);
		} catch (ClassCastException unused) {
			return false;
		}
	}

	public boolean containsValue(final Object o) {
		for (Iterator<Object> iterator = valueIterator(); iterator.hasNext();) {
			if (iterator.next().equals(o)) {
				return true;
			}
		}
		return false;
	}

	public Object valAt(final Object key) {
	    return valAt(key, null);
	}

	public Object valAt(final Object key, final Object notFound) {
	    @SuppressWarnings("unchecked")
	    final Optional<Object> result = rootNode
	    .findByKey(key, Util.hasheq(key), 0);

	    if (result.isPresent()) {
	        return result.get();
	    } else {
	        return notFound;
	    }
	}

	public IPersistentMap assoc(final Object key, final Object val) {
		final int keyHash = Util.hasheq(key);
		final MapResult details = MapResult.unchanged();

		final CompactMapNode newRootNode = rootNode.updated(null, key, val,
						keyHash, 0, details);

		if (details.isModified()) {
			if (details.hasReplacedValue()) {
				return new TrieMap_5Bits(newRootNode, cachedSize);
			}

			return new TrieMap_5Bits(newRootNode, cachedSize + 1);
		}

		return this;
	}

	public IPersistentMap without(final Object key) {
		final int keyHash = Util.hasheq(key);
		final MapResult details = MapResult.unchanged();

		final CompactMapNode newRootNode = rootNode.removed(null, key,
						keyHash, 0, details);

		if (details.isModified()) {
			assert details.hasReplacedValue();
			return new TrieMap_5Bits(newRootNode, cachedSize - 1);
		}

		return this;
	}

	public Iterator<Object> keyIterator() {
		return new MapKeyIterator(rootNode);
	}

	public Iterator<Object> valueIterator() {
		return new MapValueIterator(rootNode);
	}

	public Iterator<Map.Entry> entryIterator() {
		return new MapEntryIterator(rootNode);
	}

	@Override
	public Set<Object> keySet() {
		Set<Object> keySet = null;

		if (keySet == null) {
			keySet = new AbstractSet<Object>() {
				@Override
				public Iterator<Object> iterator() {
					return TrieMap_5Bits.this.keyIterator();
				}

				@Override
				public int size() {
					return TrieMap_5Bits.this.size();
				}

				@Override
				public boolean isEmpty() {
					return TrieMap_5Bits.this.isEmpty();
				}

				@Override
				public void clear() {
					TrieMap_5Bits.this.clear();
				}

				@Override
				public boolean contains(Object k) {
					return TrieMap_5Bits.this.containsKey(k);
				}
			};
		}

		return keySet;
	}

	@Override
	public Collection<Object> values() {
		Collection<Object> values = null;

		if (values == null) {
			values = new AbstractCollection<Object>() {
				@Override
				public Iterator<Object> iterator() {
					return TrieMap_5Bits.this.valueIterator();
				}

				@Override
				public int size() {
					return TrieMap_5Bits.this.size();
				}

				@Override
				public boolean isEmpty() {
					return TrieMap_5Bits.this.isEmpty();
				}

				@Override
				public void clear() {
					TrieMap_5Bits.this.clear();
				}

				@Override
				public boolean contains(Object v) {
					return TrieMap_5Bits.this.containsValue(v);
				}
			};
		}

		return values;
	}

	@Override
	public Set<java.util.Map.Entry> entrySet() {
		Set<java.util.Map.Entry> entrySet = null;

		if (entrySet == null) {
			entrySet = new AbstractSet<java.util.Map.Entry>() {
				@Override
				public Iterator<java.util.Map.Entry> iterator() {
					return new Iterator<Map.Entry>() {
						private final Iterator<Map.Entry> i = entryIterator();

						@Override
						public boolean hasNext() {
							return i.hasNext();
						}

						@Override
						public Map.Entry next() {
							return i.next();
						}

						@Override
						public void remove() {
							i.remove();
						}
					};
				}

				@Override
				public int size() {
					return TrieMap_5Bits.this.size();
				}

				@Override
				public boolean isEmpty() {
					return TrieMap_5Bits.this.isEmpty();
				}

				@Override
				public void clear() {
					TrieMap_5Bits.this.clear();
				}

				@Override
				public boolean contains(Object k) {
					return TrieMap_5Bits.this.containsKey(k);
				}
			};
		}

		return entrySet;
	}

	@Override
	public boolean equals(final Object other) {
		if (other == this) {
			return true;
		}
		if (other == null) {
			return false;
		}

		if (other instanceof TrieMap_5Bits) {
			TrieMap_5Bits that = (TrieMap_5Bits) other;

			if (this.cachedSize != that.cachedSize) {
				return false;
			}

			return rootNode.equals(that.rootNode);
		} else if (other instanceof Map) {
			Map that = (Map) other;

			if (this.size() != that.size())
				return false;

			for (@SuppressWarnings("unchecked")
			Iterator<Map.Entry> it = that.entrySet().iterator(); it.hasNext();) {
				Map.Entry entry = it.next();

				try {
					@SuppressWarnings("unchecked")
					final Object key = entry.getKey();
					final Optional<Object> result = rootNode.findByKey(key,
									Util.hasheq(key), 0);

					if (!result.isPresent()) {
						return false;
					} else {
						@SuppressWarnings("unchecked")
						final Object val = entry.getValue();

						if (!result.get().equals(val)) {
							return false;
						}
					}
				} catch (ClassCastException unused) {
					return false;
				}
			}

			return true;
		}

		return false;
	}

	@Override
	public ITransientCollection asTransient() {
		return new TransientTrieMap_5Bits(this);
	}

	/*
	 * For analysis purposes only.
	 */
	protected AbstractMapNode getRootNode() {
		return rootNode;
	}

	/*
	 * For analysis purposes only.
	 */
	protected Iterator<AbstractMapNode> nodeIterator() {
		return new TrieMap_5BitsNodeIterator(rootNode);
	}

	/*
	 * For analysis purposes only.
	 */
	protected int getNodeCount() {
		final Iterator<AbstractMapNode> it = nodeIterator();
		int sumNodes = 0;

		for (; it.hasNext(); it.next()) {
			sumNodes += 1;
		}

		return sumNodes;
	}

	/*
	 * For analysis purposes only. Payload X Node
	 */
	protected int[][] arityCombinationsHistogram() {
		final Iterator<AbstractMapNode> it = nodeIterator();
		final int[][] sumArityCombinations = new int[33][33];

		while (it.hasNext()) {
			final AbstractMapNode node = it.next();
			sumArityCombinations[node.payloadArity()][node.nodeArity()] += 1;
		}

		return sumArityCombinations;
	}

	abstract static class Optional<T> {
		private static final Optional EMPTY = new Optional() {
			@Override
			boolean isPresent() {
				return false;
			}

			@Override
			Object get() {
				return null;
			}
		};

		@SuppressWarnings("unchecked")
		static <T> Optional<T> empty() {
			return EMPTY;
		}

		static <T> Optional<T> of(T value) {
			return new Value<T>(value);
		}

		abstract boolean isPresent();

		abstract T get();

		private static final class Value<T> extends Optional<T> {
			private final T value;

			private Value(T value) {
				this.value = value;
			}

			@Override
			boolean isPresent() {
				return true;
			}

			@Override
			T get() {
				return value;
			}
		}
	}

	static final class MapResult {
		private Object replacedValue;
		private boolean isModified;
		private boolean isReplaced;

		// update: inserted/removed single element, element count changed
		public void modified() {
			this.isModified = true;
		}

		public void updated(Object replacedValue) {
			this.replacedValue = replacedValue;
			this.isModified = true;
			this.isReplaced = true;
		}

		// update: neither element, nor element count changed
		public static  MapResult unchanged() {
			return new MapResult();
		}

		private MapResult() {
		}

		public boolean isModified() {
			return isModified;
		}

		public boolean hasReplacedValue() {
			return isReplaced;
		}

		public Object getReplacedValue() {
			return replacedValue;
		}
	}

	protected static interface INode {
	}

	protected static abstract class AbstractMapNode implements INode {

		static final int TUPLE_LENGTH = 2;

		abstract boolean containsKey(final Object key, final int keyHash, final int shift);

		abstract Optional<Object> findByKey(final Object key, final int keyHash, final int shift);

		abstract CompactMapNode updated(final AtomicReference<Thread> mutator, final Object key,
						final Object val, final int keyHash, final int shift,
						final MapResult details);

		abstract CompactMapNode removed(final AtomicReference<Thread> mutator, final Object key,
						final int keyHash, final int shift, final MapResult details);

		static final boolean isAllowedToEdit(AtomicReference<Thread> x, AtomicReference<Thread> y) {
			return x != null && y != null && (x == y || x.get() == y.get());
		}

		abstract boolean hasNodes();

		abstract int nodeArity();

		abstract AbstractMapNode getNode(final int index);

		@Deprecated
		Iterator<? extends AbstractMapNode> nodeIterator() {
			return new Iterator<AbstractMapNode>() {

				int nextIndex = 0;
				final int nodeArity = AbstractMapNode.this.nodeArity();

				@Override
				public void remove() {
					throw new UnsupportedOperationException();
				}

				@Override
				public AbstractMapNode next() {
					if (!hasNext())
						throw new NoSuchElementException();
					return AbstractMapNode.this.getNode(nextIndex++);
				}

				@Override
				public boolean hasNext() {
					return nextIndex < nodeArity;
				}
			};
		}

		abstract boolean hasPayload();

		abstract int payloadArity();

		abstract Object getKey(final int index);

		abstract Object getValue(final int index);

		abstract Map.Entry getKeyValueEntry(final int index);

		@Deprecated
		abstract boolean hasSlots();

		abstract int slotArity();

		abstract Object getSlot(final int index);

		/**
		 * The arity of this trie node (i.e. number of values and nodes stored
		 * on this level).
		 * 
		 * @return sum of nodes and values stored within
		 */

		int arity() {
			return payloadArity() + nodeArity();
		}

		int size() {
			final Iterator<Object> it = new MapKeyIterator(this);

			int size = 0;
			while (it.hasNext()) {
				size += 1;
				it.next();
			}

			return size;
		}
	}

	protected static abstract class CompactMapNode extends AbstractMapNode {

		static final int HASH_CODE_LENGTH = 32;

		static final int BIT_PARTITION_SIZE = 5;
		static final int BIT_PARTITION_MASK = 31;

		static final int mask(final int keyHash, final int shift) {
			return (keyHash >>> shift) & BIT_PARTITION_MASK;
		}

		static final int bitpos(final int mask) {
			return (int) (1 << mask);
		}

		abstract int nodeMap();

		abstract int dataMap();

		static final byte SIZE_EMPTY = 0;
		static final byte SIZE_ONE = 1;
		static final byte SIZE_MORE_THAN_ONE = 2;

		/**
		 * Abstract predicate over a node's size. Value can be either
		 * {@value #SIZE_EMPTY}, {@value #SIZE_ONE}, or
		 * {@value #SIZE_MORE_THAN_ONE}.
		 * 
		 * @return size predicate
		 */
		abstract byte sizePredicate();

		@Override
		abstract CompactMapNode getNode(final int index);

		boolean nodeInvariant() {
			boolean inv1 = (size() - payloadArity() >= 2 * (arity() - payloadArity()));
			boolean inv2 = (this.arity() == 0) ? sizePredicate() == SIZE_EMPTY : true;
			boolean inv3 = (this.arity() == 1 && payloadArity() == 1) ? sizePredicate() == SIZE_ONE
							: true;
			boolean inv4 = (this.arity() >= 2) ? sizePredicate() == SIZE_MORE_THAN_ONE : true;

			boolean inv5 = (this.nodeArity() >= 0) && (this.payloadArity() >= 0)
							&& ((this.payloadArity() + this.nodeArity()) == this.arity());

			return inv1 && inv2 && inv3 && inv4 && inv5;
		}

		abstract CompactMapNode copyAndSetValue(final AtomicReference<Thread> mutator,
						final int bitpos, final Object val);

		abstract CompactMapNode copyAndInsertValue(final AtomicReference<Thread> mutator,
						final int bitpos, final Object key, final Object val);

		abstract CompactMapNode copyAndRemoveValue(final AtomicReference<Thread> mutator,
						final int bitpos);

		abstract CompactMapNode copyAndSetNode(final AtomicReference<Thread> mutator,
						final int bitpos, final CompactMapNode node);

		abstract CompactMapNode copyAndMigrateFromInlineToNode(
						final AtomicReference<Thread> mutator, final int bitpos,
						final CompactMapNode node);

		abstract CompactMapNode copyAndMigrateFromNodeToInline(
						final AtomicReference<Thread> mutator, final int bitpos,
						final CompactMapNode node);

		static final  CompactMapNode mergeTwoKeyValPairs(final Object key0, final Object val0,
						final int keyHash0, final Object key1, final Object val1, final int keyHash1,
						final int shift) {
			assert !(key0.equals(key1));

			if (shift >= HASH_CODE_LENGTH) {
				// throw new
				// IllegalStateException("Hash collision not yet fixed.");
				return new HashCollisionMapNode_5Bits(keyHash0,
								new Object[] { key0, key1 },
								new Object[] { val0, val1 });
			}

			final int mask0 = mask(keyHash0, shift);
			final int mask1 = mask(keyHash1, shift);

			if (mask0 != mask1) {
				// both nodes fit on same level
				final int dataMap = (int) (bitpos(mask0) | bitpos(mask1));

				if (mask0 < mask1) {
					return nodeOf(null, (int) (0), dataMap, new Object[] { key0, val0, key1, val1 });
				} else {
					return nodeOf(null, (int) (0), dataMap, new Object[] { key1, val1, key0, val0 });
				}
			} else {
				final CompactMapNode node = mergeTwoKeyValPairs(key0, val0, keyHash0, key1,
								val1, keyHash1, shift + BIT_PARTITION_SIZE);
				// values fit on next level

				final int nodeMap = bitpos(mask0);
				return nodeOf(null, nodeMap, (int) (0), new Object[] { node });
			}
		}

		static final CompactMapNode EMPTY_NODE;

		static {

			EMPTY_NODE = new BitmapIndexedMapNode(null, (int) (0), (int) (0), new Object[] {});

		};

		static final  CompactMapNode nodeOf(final AtomicReference<Thread> mutator,
						final int nodeMap, final int dataMap, final Object[] nodes) {
			return new BitmapIndexedMapNode(mutator, nodeMap, dataMap, nodes);
		}

		@SuppressWarnings("unchecked")
		static final  CompactMapNode nodeOf(AtomicReference<Thread> mutator) {
			return EMPTY_NODE;
		}

		static final  CompactMapNode nodeOf(AtomicReference<Thread> mutator,
						final int nodeMap, final int dataMap, final Object key, final Object val) {
			assert nodeMap == 0;
			return nodeOf(mutator, (int) (0), dataMap, new Object[] { key, val });
		}

		static final int index(final int bitmap, final int bitpos) {
			return java.lang.Integer.bitCount(bitmap & (bitpos - 1));
		}

		static final int index(final int bitmap, final int mask, final int bitpos) {
			return (bitmap == -1) ? mask : index(bitmap, bitpos);
		}

		int dataIndex(final int bitpos) {
			return java.lang.Integer.bitCount(dataMap() & (bitpos - 1));
		}

		int nodeIndex(final int bitpos) {
			return java.lang.Integer.bitCount(nodeMap() & (bitpos - 1));
		}

		CompactMapNode nodeAt(final int bitpos) {
			return getNode(nodeIndex(bitpos));
		}

		boolean containsKey(final Object key, final int keyHash, final int shift) {
			final int mask = mask(keyHash, shift);
			final int bitpos = bitpos(mask);

			final int dataMap = dataMap();
			if ((dataMap & bitpos) != 0) {
				final int index = index(dataMap, mask, bitpos);
				return Util.equiv(getKey(index), key);
			}

			final int nodeMap = nodeMap();
			if ((nodeMap & bitpos) != 0) {
				final int index = index(nodeMap, mask, bitpos);
				return getNode(index).containsKey(key, keyHash, shift + BIT_PARTITION_SIZE);
			}

			return false;
		}

		Optional<Object> findByKey(final Object key, final int keyHash, final int shift) {
			final int mask = mask(keyHash, shift);
			final int bitpos = bitpos(mask);

			if ((dataMap() & bitpos) != 0) { // inplace value
				final int index = dataIndex(bitpos);
				if (Util.equiv(getKey(index), key)) {
					final Object result = getValue(index);

					return Optional.of(result);
				}

				return Optional.empty();
			}

			if ((nodeMap() & bitpos) != 0) { // node (not value)
				final AbstractMapNode subNode = nodeAt(bitpos);

				return subNode.findByKey(key, keyHash, shift + BIT_PARTITION_SIZE);
			}

			return Optional.empty();
		}

		CompactMapNode updated(final AtomicReference<Thread> mutator, final Object key,
						final Object val, final int keyHash, final int shift,
						final MapResult details) {
			final int mask = mask(keyHash, shift);
			final int bitpos = bitpos(mask);

			if ((dataMap() & bitpos) != 0) { // inplace value
				final int dataIndex = dataIndex(bitpos);
				final Object currentKey = getKey(dataIndex);

				if (Util.equiv(currentKey, key)) {
					final Object currentVal = getValue(dataIndex);

					// update mapping
					details.updated(currentVal);
					return copyAndSetValue(mutator, bitpos, val);
				} else {
					final Object currentVal = getValue(dataIndex);
					final CompactMapNode subNodeNew = mergeTwoKeyValPairs(currentKey,
									currentVal, Util.hasheq(currentKey), key, val,
									keyHash, shift + BIT_PARTITION_SIZE);

					details.modified();
					return copyAndMigrateFromInlineToNode(mutator, bitpos, subNodeNew);
				}
			} else if ((nodeMap() & bitpos) != 0) { // node (not value)
				final CompactMapNode subNode = nodeAt(bitpos);
				final CompactMapNode subNodeNew = subNode.updated(mutator, key, val, keyHash,
								shift + BIT_PARTITION_SIZE, details);

				if (details.isModified()) {
					return copyAndSetNode(mutator, bitpos, subNodeNew);
				} else {
					return this;
				}
			} else {
				// no value
				details.modified();
				return copyAndInsertValue(mutator, bitpos, key, val);
			}
		}

		CompactMapNode removed(final AtomicReference<Thread> mutator, final Object key,
						final int keyHash, final int shift, final MapResult details) {
			final int mask = mask(keyHash, shift);
			final int bitpos = bitpos(mask);

			if ((dataMap() & bitpos) != 0) { // inplace value
				final int dataIndex = dataIndex(bitpos);

				if (Util.equiv(getKey(dataIndex), key)) {
					final Object currentVal = getValue(dataIndex);
					details.updated(currentVal);

					if (this.payloadArity() == 2 && this.nodeArity() == 0) {
						/*
						 * Create new node with remaining pair. The new node
						 * will a) either become the new root returned, or b)
						 * unwrapped and inlined during returning.
						 */
						final int newDataMap = (shift == 0) ? (int) (dataMap() ^ bitpos)
										: bitpos(mask(keyHash, 0));

						if (dataIndex == 0) {
							return CompactMapNode. nodeOf(mutator, (int) 0, newDataMap,
											getKey(1), getValue(1));
						} else {
							return CompactMapNode. nodeOf(mutator, (int) 0, newDataMap,
											getKey(0), getValue(0));
						}
					} else {
						return copyAndRemoveValue(mutator, bitpos);
					}
				} else {
					return this;
				}
			} else if ((nodeMap() & bitpos) != 0) { // node (not value)
				final CompactMapNode subNode = nodeAt(bitpos);
				final CompactMapNode subNodeNew = subNode.removed(mutator, key, keyHash,
								shift + BIT_PARTITION_SIZE, details);

				if (!details.isModified()) {
					return this;
				}

				switch (subNodeNew.sizePredicate()) {
				case 0: {
					throw new IllegalStateException("Sub-node must have at least one element.");
				}
				case 1: {
					if (this.payloadArity() == 0 && this.nodeArity() == 1) {
						// escalate (singleton or empty) result
						return subNodeNew;
					} else {
						// inline value (move to front)
						return copyAndMigrateFromNodeToInline(mutator, bitpos, subNodeNew);
					}
				}
				default: {
					// modify current node (set replacement node)
					return copyAndSetNode(mutator, bitpos, subNodeNew);
				}
				}
			}

			return this;
		}

		/**
		 * @return 0 <= mask <= 2^BIT_PARTITION_SIZE - 1
		 */
		static byte recoverMask(int map, byte i_th) {
			assert 1 <= i_th && i_th <= 32;

			byte cnt1 = 0;
			byte mask = 0;

			while (mask < 32) {
				if ((map & 0x01) == 0x01) {
					cnt1 += 1;

					if (cnt1 == i_th) {
						return mask;
					}
				}

				map = (int) (map >> 1);
				mask += 1;
			}

			assert cnt1 != i_th;
			throw new RuntimeException("Called with invalid arguments.");
		}

	}

	protected static abstract class CompactMixedMapNode extends CompactMapNode {

		private final int nodeMap;
		private final int dataMap;

		CompactMixedMapNode(final AtomicReference<Thread> mutator, final int nodeMap,
						final int dataMap) {
			this.nodeMap = nodeMap;
			this.dataMap = dataMap;
		}

		@Override
		public int nodeMap() {
			return nodeMap;
		}

		@Override
		public int dataMap() {
			return dataMap;
		}

	}

	private static final class BitmapIndexedMapNode extends CompactMixedMapNode {

		final AtomicReference<Thread> mutator;
		final Object[] nodes;

		private BitmapIndexedMapNode(final AtomicReference<Thread> mutator, final int nodeMap,
						final int dataMap, final Object[] nodes) {
			super(mutator, nodeMap, dataMap);

			this.mutator = mutator;
			this.nodes = nodes;

			if (DEBUG) {

				assert (TUPLE_LENGTH * java.lang.Integer.bitCount(dataMap)
								+ java.lang.Integer.bitCount(nodeMap) == nodes.length);

				for (int i = 0; i < TUPLE_LENGTH * payloadArity(); i++) {
					assert ((nodes[i] instanceof CompactMapNode) == false);
				}
				for (int i = TUPLE_LENGTH * payloadArity(); i < nodes.length; i++) {
					assert ((nodes[i] instanceof CompactMapNode) == true);
				}
			}

			assert nodeInvariant();
		}

		@SuppressWarnings("unchecked")
		@Override
		Object getKey(final int index) {
			return nodes[TUPLE_LENGTH * index];
		}

		@SuppressWarnings("unchecked")
		@Override
		Object getValue(final int index) {
			return nodes[TUPLE_LENGTH * index + 1];
		}

		Map.Entry getKeyValueEntry(final int index) {
			return entryOf(nodes[TUPLE_LENGTH * index], nodes[TUPLE_LENGTH * index + 1]);
		}

		@SuppressWarnings("unchecked")
		@Override
		CompactMapNode getNode(final int index) {
			return (CompactMapNode) nodes[nodes.length - 1 - index];
		}

		@Override
		boolean hasPayload() {
			return dataMap() != 0;
		}

		@Override
		int payloadArity() {
			return java.lang.Integer.bitCount(dataMap());
		}

		@Override
		boolean hasNodes() {
			return nodeMap() != 0;
		}

		@Override
		int nodeArity() {
			return java.lang.Integer.bitCount(nodeMap());
		}

		@Override
		Object getSlot(final int index) {
			return nodes[index];
		}

		@Override
		boolean hasSlots() {
			return nodes.length != 0;
		}

		@Override
		int slotArity() {
			return nodes.length;
		}

		@Override
		public boolean equals(final Object other) {
			if (null == other) {
				return false;
			}
			if (this == other) {
				return true;
			}
			if (getClass() != other.getClass()) {
				return false;
			}
			BitmapIndexedMapNode that = (BitmapIndexedMapNode) other;
			if (nodeMap() != that.nodeMap()) {
				return false;
			}
			if (dataMap() != that.dataMap()) {
				return false;
			}
			int len = Math.min(nodes.length, that.nodes.length);
			for(int i = 0; i < len; i++)
			    if (!Util.equiv(nodes[i], that.nodes[i])) return false;
			return true;
		}

		@Override
		byte sizePredicate() {
			if (this.nodeArity() == 0) {
				switch (this.payloadArity()) {
				case 0:
					return SIZE_EMPTY;
				case 1:
					return SIZE_ONE;
				default:
					return SIZE_MORE_THAN_ONE;
				}
			} else {
				return SIZE_MORE_THAN_ONE;
			}
		}

		@Override
		CompactMapNode copyAndSetValue(final AtomicReference<Thread> mutator,
						final int bitpos, final Object val) {
			final int idx = TUPLE_LENGTH * dataIndex(bitpos) + 1;

			if (isAllowedToEdit(this.mutator, mutator)) {
				// no copying if already editable
				this.nodes[idx] = val;
				return this;
			} else {
				final Object[] src = this.nodes;
				final Object[] dst = new Object[src.length];

				// copy 'src' and set 1 element(s) at position 'idx'
				System.arraycopy(src, 0, dst, 0, src.length);
				dst[idx + 0] = val;

				return nodeOf(mutator, nodeMap(), dataMap(), dst);
			}
		}

		@Override
		CompactMapNode copyAndSetNode(final AtomicReference<Thread> mutator,
						final int bitpos, final CompactMapNode node) {

			final int idx = this.nodes.length - 1 - nodeIndex(bitpos);

			if (isAllowedToEdit(this.mutator, mutator)) {
				// no copying if already editable
				this.nodes[idx] = node;
				return this;
			} else {
				final Object[] src = this.nodes;
				final Object[] dst = new Object[src.length];

				// copy 'src' and set 1 element(s) at position 'idx'
				System.arraycopy(src, 0, dst, 0, src.length);
				dst[idx + 0] = node;

				return nodeOf(mutator, nodeMap(), dataMap(), dst);
			}
		}

		@Override
		CompactMapNode copyAndInsertValue(final AtomicReference<Thread> mutator,
						final int bitpos, final Object key, final Object val) {
			final int idx = TUPLE_LENGTH * dataIndex(bitpos);

			final Object[] src = this.nodes;
			final Object[] dst = new Object[src.length + 2];

			// copy 'src' and insert 2 element(s) at position 'idx'
			System.arraycopy(src, 0, dst, 0, idx);
			dst[idx + 0] = key;
			dst[idx + 1] = val;
			System.arraycopy(src, idx, dst, idx + 2, src.length - idx);

			return nodeOf(mutator, nodeMap(), (int) (dataMap() | bitpos), dst);
		}

		@Override
		CompactMapNode copyAndRemoveValue(final AtomicReference<Thread> mutator,
						final int bitpos) {
			final int idx = TUPLE_LENGTH * dataIndex(bitpos);

			final Object[] src = this.nodes;
			final Object[] dst = new Object[src.length - 2];

			// copy 'src' and remove 2 element(s) at position 'idx'
			System.arraycopy(src, 0, dst, 0, idx);
			System.arraycopy(src, idx + 2, dst, idx, src.length - idx - 2);

			return nodeOf(mutator, nodeMap(), (int) (dataMap() ^ bitpos), dst);
		}

		@Override
		CompactMapNode copyAndMigrateFromInlineToNode(final AtomicReference<Thread> mutator,
						final int bitpos, final CompactMapNode node) {

			final int idxOld = TUPLE_LENGTH * dataIndex(bitpos);
			final int idxNew = this.nodes.length - TUPLE_LENGTH - nodeIndex(bitpos);

			final Object[] src = this.nodes;
			final Object[] dst = new Object[src.length - 2 + 1];

			// copy 'src' and remove 2 element(s) at position 'idxOld' and
			// insert 1 element(s) at position 'idxNew' (TODO: carefully test)
			assert idxOld <= idxNew;
			System.arraycopy(src, 0, dst, 0, idxOld);
			System.arraycopy(src, idxOld + 2, dst, idxOld, idxNew - idxOld);
			dst[idxNew + 0] = node;
			System.arraycopy(src, idxNew + 2, dst, idxNew + 1, src.length - idxNew - 2);

			return nodeOf(mutator, (int) (nodeMap() | bitpos), (int) (dataMap() ^ bitpos), dst);
		}

		@Override
		CompactMapNode copyAndMigrateFromNodeToInline(final AtomicReference<Thread> mutator,
						final int bitpos, final CompactMapNode node) {

			final int idxOld = this.nodes.length - 1 - nodeIndex(bitpos);
			final int idxNew = TUPLE_LENGTH * dataIndex(bitpos);

			final Object[] src = this.nodes;
			final Object[] dst = new Object[src.length - 1 + 2];

			// copy 'src' and remove 1 element(s) at position 'idxOld' and
			// insert 2 element(s) at position 'idxNew' (TODO: carefully test)
			assert idxOld >= idxNew;
			System.arraycopy(src, 0, dst, 0, idxNew);
			dst[idxNew + 0] = node.getKey(0);
			dst[idxNew + 1] = node.getValue(0);
			System.arraycopy(src, idxNew, dst, idxNew + 2, idxOld - idxNew);
			System.arraycopy(src, idxOld + 1, dst, idxOld + 2, src.length - idxOld - 1);

			return nodeOf(mutator, (int) (nodeMap() ^ bitpos), (int) (dataMap() | bitpos), dst);
		}

	}

	private static final class HashCollisionMapNode_5Bits extends CompactMapNode {
		private final Object[] keys;
		private final Object[] vals;
		private final int hash;

		HashCollisionMapNode_5Bits(final int hash, final Object[] keys, final Object[] vals) {
			this.keys = keys;
			this.vals = vals;
			this.hash = hash;

			assert payloadArity() >= 2;
		}

		boolean containsKey(final Object key, final int keyHash, final int shift) {
			if (this.hash == keyHash) {
				for (Object k : keys) {
					if (Util.equiv(k, key)) {
						return true;
					}
				}
			}
			return false;
		}

		Optional<Object> findByKey(final Object key, final int keyHash, final int shift) {
			for (int i = 0; i < keys.length; i++) {
				final Object _key = keys[i];
				if (Util.equiv(key, _key)) {
					final Object val = vals[i];
					return Optional.of(val);
				}
			}
			return Optional.empty();
		}

		CompactMapNode updated(final AtomicReference<Thread> mutator, final Object key,
						final Object val, final int keyHash, final int shift,
						final MapResult details) {
			assert this.hash == keyHash;

			for (int idx = 0; idx < keys.length; idx++) {
				if (Util.equiv(keys[idx], key)) {
					final Object currentVal = vals[idx];

					if (currentVal.equals(val)) {
						return this;
					} else {
						// add new mapping
						final Object[] src = this.vals;
						@SuppressWarnings("unchecked")
						final Object[] dst = new Object[src.length];

						// copy 'src' and set 1 element(s) at position 'idx'
						System.arraycopy(src, 0, dst, 0, src.length);
						dst[idx + 0] = val;

						final CompactMapNode thisNew = new HashCollisionMapNode_5Bits(
										this.hash, this.keys, dst);

						details.updated(currentVal);
						return thisNew;
					}
				}
			}

			@SuppressWarnings("unchecked")
			final Object[] keysNew = new Object[this.keys.length + 1];

			// copy 'this.keys' and insert 1 element(s) at position
			// 'keys.length'
			System.arraycopy(this.keys, 0, keysNew, 0, keys.length);
			keysNew[keys.length + 0] = key;
			System.arraycopy(this.keys, keys.length, keysNew, keys.length + 1, this.keys.length
							- keys.length);

			@SuppressWarnings("unchecked")
			final Object[] valsNew = new Object[this.vals.length + 1];

			// copy 'this.vals' and insert 1 element(s) at position
			// 'vals.length'
			System.arraycopy(this.vals, 0, valsNew, 0, vals.length);
			valsNew[vals.length + 0] = val;
			System.arraycopy(this.vals, vals.length, valsNew, vals.length + 1, this.vals.length
							- vals.length);

			details.modified();
			return new HashCollisionMapNode_5Bits(keyHash, keysNew, valsNew);
		}

		CompactMapNode removed(final AtomicReference<Thread> mutator, final Object key,
						final int keyHash, final int shift, final MapResult details) {
			for (int idx = 0; idx < keys.length; idx++) {
				if (Util.equiv(keys[idx], key)) {
					final Object currentVal = vals[idx];
					details.updated(currentVal);

					if (this.arity() == 1) {
						return nodeOf(mutator);
					} else if (this.arity() == 2) {
						/*
						 * Create root node with singleton element. This node
						 * will be a) either be the new root returned, or b)
						 * unwrapped and inlined.
						 */
						final Object theOtherKey = (idx == 0) ? keys[1] : keys[0];
						final Object theOtherVal = (idx == 0) ? vals[1] : vals[0];
						return CompactMapNode. nodeOf(mutator).updated(mutator, theOtherKey,
										theOtherVal, keyHash, 0, details);
					} else {
						@SuppressWarnings("unchecked")
						final Object[] keysNew = new Object[this.keys.length - 1];

						// copy 'this.keys' and remove 1 element(s) at position
						// 'idx'
						System.arraycopy(this.keys, 0, keysNew, 0, idx);
						System.arraycopy(this.keys, idx + 1, keysNew, idx, this.keys.length - idx
										- 1);

						@SuppressWarnings("unchecked")
						final Object[] valsNew = new Object[this.vals.length - 1];

						// copy 'this.vals' and remove 1 element(s) at position
						// 'idx'
						System.arraycopy(this.vals, 0, valsNew, 0, idx);
						System.arraycopy(this.vals, idx + 1, valsNew, idx, this.vals.length - idx
										- 1);

						return new HashCollisionMapNode_5Bits(keyHash, keysNew, valsNew);
					}
				}
			}
			return this;
		}

		@Override
		boolean hasPayload() {
			return true;
		}

		@Override
		int payloadArity() {
			return keys.length;
		}

		@Override
		boolean hasNodes() {
			return false;
		}

		@Override
		int nodeArity() {
			return 0;
		}

		@Override
		int arity() {
			return payloadArity();
		}

		@Override
		byte sizePredicate() {
			return SIZE_MORE_THAN_ONE;
		}

		@Override
		Object getKey(final int index) {
			return keys[index];
		}

		@Override
		Object getValue(final int index) {
			return vals[index];
		}

		Map.Entry getKeyValueEntry(final int index) {
			return entryOf(keys[index], vals[index]);
		}

		@Override
		public CompactMapNode getNode(int index) {
			throw new IllegalStateException("Is leaf node.");
		}

		@Override
		Object getSlot(final int index) {
			throw new UnsupportedOperationException();
		}

		@Override
		boolean hasSlots() {
			throw new UnsupportedOperationException();
		}

		@Override
		int slotArity() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean equals(Object other) {
			if (null == other) {
				return false;
			}
			if (this == other) {
				return true;
			}
			if (getClass() != other.getClass()) {
				return false;
			}

			HashCollisionMapNode_5Bits that = (HashCollisionMapNode_5Bits) other;

			if (hash != that.hash) {
				return false;
			}

			if (arity() != that.arity()) {
				return false;
			}

			/*
			 * Linear scan for each key, because of arbitrary element order.
			 */
			outerLoop: for (int i = 0; i < that.payloadArity(); i++) {
				final Object otherKey = that.getKey(i);
				final Object otherVal = that.getValue(i);

				for (int j = 0; j < keys.length; j++) {
					final Object key = keys[j];
					final Object val = vals[j];

					if (Util.equiv(key, otherKey) && Util.equiv(val, otherVal)) {
						continue outerLoop;
					}
				}
				return false;
			}

			return true;
		}

		@Override
		CompactMapNode copyAndSetValue(final AtomicReference<Thread> mutator,
						final int bitpos, final Object val) {
			throw new UnsupportedOperationException();
		}

		@Override
		CompactMapNode copyAndInsertValue(final AtomicReference<Thread> mutator,
						final int bitpos, final Object key, final Object val) {
			throw new UnsupportedOperationException();
		}

		@Override
		CompactMapNode copyAndRemoveValue(final AtomicReference<Thread> mutator,
						final int bitpos) {
			throw new UnsupportedOperationException();
		}

		@Override
		CompactMapNode copyAndSetNode(final AtomicReference<Thread> mutator,
						final int bitpos, final CompactMapNode node) {
			throw new UnsupportedOperationException();
		}

		@Override
		CompactMapNode copyAndMigrateFromInlineToNode(final AtomicReference<Thread> mutator,
						final int bitpos, final CompactMapNode node) {
			throw new UnsupportedOperationException();
		}

		@Override
		CompactMapNode copyAndMigrateFromNodeToInline(final AtomicReference<Thread> mutator,
						final int bitpos, final CompactMapNode node) {
			throw new UnsupportedOperationException();
		}

		@Override
		int nodeMap() {
			throw new UnsupportedOperationException();
		}

		@Override
		int dataMap() {
			throw new UnsupportedOperationException();
		}

	}

	/**
	 * Iterator skeleton that uses a fixed stack in depth.
	 */
	private static abstract class AbstractMapIterator {

		private static final int MAX_DEPTH = 7;

		protected int currentValueCursor;
		protected int currentValueLength;
		protected AbstractMapNode currentValueNode;

		private int currentStackLevel = -1;
		private final int[] nodeCursorsAndLengths = new int[MAX_DEPTH * 2];

		@SuppressWarnings("unchecked")
		AbstractMapNode[] nodes = new AbstractMapNode[MAX_DEPTH];

		AbstractMapIterator(AbstractMapNode rootNode) {
			if (rootNode.hasNodes()) {
				currentStackLevel = 0;

				nodes[0] = rootNode;
				nodeCursorsAndLengths[0] = 0;
				nodeCursorsAndLengths[1] = rootNode.nodeArity();
			}

			if (rootNode.hasPayload()) {
				currentValueNode = rootNode;
				currentValueCursor = 0;
				currentValueLength = rootNode.payloadArity();
			}
		}

		/*
		 * search for next node that contains values
		 */
		private boolean searchNextValueNode() {
			while (currentStackLevel >= 0) {
				final int currentCursorIndex = currentStackLevel * 2;
				final int currentLengthIndex = currentCursorIndex + 1;

				final int nodeCursor = nodeCursorsAndLengths[currentCursorIndex];
				final int nodeLength = nodeCursorsAndLengths[currentLengthIndex];

				if (nodeCursor < nodeLength) {
					final AbstractMapNode nextNode = nodes[currentStackLevel]
									.getNode(nodeCursor);
					nodeCursorsAndLengths[currentCursorIndex]++;

					if (nextNode.hasNodes()) {
						/*
						 * put node on next stack level for depth-first
						 * traversal
						 */
						final int nextStackLevel = ++currentStackLevel;
						final int nextCursorIndex = nextStackLevel * 2;
						final int nextLengthIndex = nextCursorIndex + 1;

						nodes[nextStackLevel] = nextNode;
						nodeCursorsAndLengths[nextCursorIndex] = 0;
						nodeCursorsAndLengths[nextLengthIndex] = nextNode.nodeArity();
					}

					if (nextNode.hasPayload()) {
						/*
						 * found next node that contains values
						 */
						currentValueNode = nextNode;
						currentValueCursor = 0;
						currentValueLength = nextNode.payloadArity();
						return true;
					}
				} else {
					currentStackLevel--;
				}
			}

			return false;
		}

		public boolean hasNext() {
			if (currentValueCursor < currentValueLength) {
				return true;
			} else {
				return searchNextValueNode();
			}
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	protected static class MapKeyIterator extends AbstractMapIterator implements
					Iterator<Object> {

		MapKeyIterator(AbstractMapNode rootNode) {
			super(rootNode);
		}

		@Override
		public Object next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			} else {
				return currentValueNode.getKey(currentValueCursor++);
			}
		}

	}

	protected static class MapValueIterator extends AbstractMapIterator implements
					Iterator<Object> {

		MapValueIterator(AbstractMapNode rootNode) {
			super(rootNode);
		}

		@Override
		public Object next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			} else {
				return currentValueNode.getValue(currentValueCursor++);
			}
		}

	}

	protected static class MapEntryIterator extends AbstractMapIterator implements
					Iterator<Map.Entry> {

		MapEntryIterator(AbstractMapNode rootNode) {
			super(rootNode);
		}

		@Override
		public Map.Entry next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			} else {
				return currentValueNode.getKeyValueEntry(currentValueCursor++);
			}
		}

	}

	/**
	 * Iterator that first iterates over inlined-values and then continues depth
	 * first recursively.
	 */
	private static class TrieMap_5BitsNodeIterator implements Iterator<AbstractMapNode> {

		final Deque<Iterator<? extends AbstractMapNode>> nodeIteratorStack;

		TrieMap_5BitsNodeIterator(AbstractMapNode rootNode) {
			nodeIteratorStack = new ArrayDeque();
			nodeIteratorStack.push(Collections.singleton(rootNode).iterator());
		}

		@Override
		public boolean hasNext() {
			while (true) {
				if (nodeIteratorStack.isEmpty()) {
					return false;
				} else {
					if (nodeIteratorStack.peek().hasNext()) {
						return true;
					} else {
						nodeIteratorStack.pop();
						continue;
					}
				}
			}
		}

		@Override
		public AbstractMapNode next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}

			AbstractMapNode innerNode = nodeIteratorStack.peek().next();

			if (innerNode.hasNodes()) {
				nodeIteratorStack.push(innerNode.nodeIterator());
			}

			return innerNode;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	static final class TransientTrieMap_5Bits implements ITransientMap {
		final private AtomicReference<Thread> mutator;
		private AbstractMapNode rootNode;
		private int cachedSize;

		TransientTrieMap_5Bits(TrieMap_5Bits trieMap_5Bits) {
			this.mutator = new AtomicReference<Thread>(Thread.currentThread());
			this.rootNode = trieMap_5Bits.rootNode;
			this.cachedSize = trieMap_5Bits.cachedSize;
		}

		public Object valAt(final Object o) {
		    return valAt(o, null);
		}
		
		public Object valAt(final Object o, final Object notFound) {
			try {
				@SuppressWarnings("unchecked")
				final Object key = o;
				final Optional<Object> result = rootNode.findByKey(key,
								Util.hasheq(key), 0);

				if (result.isPresent()) {
					return result.get();
				} else {
					return notFound;
				}
			} catch (ClassCastException unused) {
				return null;
			}
		}

		public ITransientMap assoc(final Object key, final Object val) {
			if (mutator.get() == null) {
				throw new IllegalStateException("Transient already frozen.");
			}

			final int keyHash = Util.hasheq(key);
			final MapResult details = MapResult.unchanged();

			final CompactMapNode newRootNode = rootNode.updated(mutator, key, val,
							keyHash, 0, details);

			if (details.isModified()) {
				if (details.hasReplacedValue()) {
					rootNode = newRootNode;

					return this;
				} else {
					rootNode = newRootNode;
					cachedSize += 1;

					return this;
				}
			}

			return null;
		}

		public ITransientMap without(final Object key) {
			if (mutator.get() == null) {
				throw new IllegalStateException("Transient already frozen.");
			}

			final int keyHash = Util.hasheq(key);
			final MapResult details = MapResult.unchanged();

			final CompactMapNode newRootNode = rootNode.removed(mutator, key,
							keyHash, 0, details);

			if (details.isModified()) {
				assert details.hasReplacedValue();

				rootNode = newRootNode;
				cachedSize = cachedSize - 1;

				return this;
			}

			return null;
		}

		@Override
		public IPersistentMap persistent() {
			if (mutator.get() == null) {
				throw new IllegalStateException("Transient already frozen.");
			}

			mutator.set(null);
			return new TrieMap_5Bits(rootNode, cachedSize);
		}

        public ITransientCollection conj(Object o) {
            if (mutator.get() == null) {
                throw new IllegalStateException("Transient already frozen.");
            }
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

        public int count() {
            return cachedSize;
        }
	}

    @Override
    public IPersistentMap assocEx(Object key, Object val) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public Iterator iterator() {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public IMapEntry entryAt(Object key) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public int count() {
        return cachedSize;
    }

    @Override
    public IPersistentCollection empty() {
        return EMPTY_MAP;
    }

    @Override
    public ISeq seq() {
        throw new UnsupportedOperationException("TODO");
    }

}
