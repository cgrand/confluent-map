# confluent-map

A confluent hash map for Clojure.

This is a variation on the implementation of persistent hash maps.

This implementation is not expected to be slower than current Clojure maps and should be tighter on memory. (This codebase also contains a clojurified version of the [CHAMP persistent map](https://github.com/msteindorfer/oopsla15-artifact/blob/master/pdb.values/src/org/eclipse/imp/pdb/facts/util/TrieMap_5Bits.java) for comparative benchmarking.)

It features new operations (`merge3` and `merge2`) to efficiently merge two maps together (`merge3` is a 3-way merge: you provide a common ancestor to the two maps to help resolve conflicts). The complexity of this merge is _O(number of touched keys)_ so merging big maps is not a problem.

Both `merge3` and `merge2` takes an optional argument: a `fix` function to resolve conflicts. It is thus possible to merge nested maps.

The map resulting from a merge may share structure with both merged maps, making it confluent.

This can be used to hand a big map to workers and merge back results. This could also be used to compute the intersection of two substitutions.

## Usage

```clojure
[net.cgrand/confluent-map "0.1.0-SNAPSHOT"]
```

```clojure
user=>  (use '[net.cgrand.confluent-map :only [e merge3 merge2]])
nil
user=> (def m (assoc e :a 1 :b 2 :c 3))
#'user/m
user=> (def a (assoc m :a "A"))
#'user/a
user=> (def b (-> m (assoc :b "B") (dissoc :c)))
#'user/b
user=> (merge3 m a b)
{:b "B", :a "A"}
;; compare with standard merge:
user=> (merge a b)
{:c 3, :b "B", :a 1}
```

## Implementation

Ideas behind this implementation tracks back to Euroclojure 2015, my use of a 32 additional bits to make the array compact is an independent reinvention of the [Michael Steindorfer's work](http://michael.steindorfer.name/publications/oopsla15.pdf).

However from this paper I took the idea that collisions should only occur at the deepest level. This has three benefits:

* all inner nodes are of the same type,
* it lowers the number of cases to deal with for canonicalization and merge,
* it frees one of the four codes (which was originally used to signal a collision node) for ownership coding.

### Long bitmap
The present implementation uses a single 64-bit bitmap, while nodes are still 32-way. So each branch gets 2 consecutive bits (branch #N gets bits 2N and 2N+1). `00` means no branch, `11` means inlined key-value pair, both `01` and `10` mean regular branch. Using this encoding the number of bits set to 1 is the size of the array. So the array is compact like in the CHAMP paper but, unlike CHAMP, not sorted: nodes and inlined pairs are not segregated.

### Redundant branch coding and transient state machine
The redundancy in regular node coding (`10` and `01`) is used for transients.

Usually transients have been managed using an edit field (a reference to a token object) in each node (so 32 or 64 bits per node), here by using the redundant encoding it's free (strictly speaking from an information theory point of view it's 0.42 bit per node).

The ownership is thus coded like this:

* `01` means the branch is shared (and thus not suitable for transient edition).
* `10` means the node is the exclusive owner of the branch so, if the node is itself unshared, the branch is suitable for transient edition.

When a node is copied to become transient, all `10`s in its bitmap are flushed to `01`. This maintains the invariant that its branches are still shared (not editable).

### Canonicalization
The trie maintains a canonical shape (which is important for merge, and failfast equality -- not implemented yet).

### Incident allocations
No short-lived objects are allocated (eg boxes) and only static methods are used for traversal of the trie.

Boxes are unneeded for `assoc` because each node caches its count. Caching of count is also required by `merge`.

## TODO

* `dissoc!`, `with-meta`, `fold`, better `reduce`
* decide whether merge should raise more conflicts (especially when both side agrees on a modification – this could still be automatically resolved in `merge[23]`) so as to be able to implement `merge-with`.
* rigorous testing
* thorough benchmarking

## Thanks
To Ghadi Shayban and Mohit Thatte for piquing me to revisit hash maps. 

## License

Copyright © 2016 Christophe Grand

Copyright © 2015 Michael.Steindorfer@cwi.nl for TrieMap_5Bits

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
