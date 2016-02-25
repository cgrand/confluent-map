# confluent-map

A confluent hash map for Clojure.

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

This codebase also contains a clojurified version of the [CHAMP persistent map](https://github.com/msteindorfer/oopsla15-artifact/blob/master/pdb.values/src/org/eclipse/imp/pdb/facts/util/TrieMap_5Bits.java) for comparative benchmarking.

## Implementation
The array in each node is compact (no unneeded null) but, contrary to CHAMP, inline kvs and nodes are mixed.

Transients are managed by a state machine whose state is encoded in the bitmap.

The trie maintains a canonical shape (which is important for merge, and failfast equality -- not implemented yet).

No short-lived objects are allocated (eg boxes) and only static methods are used for traversal of the trie.

## License

Copyright © 2016 Christophe Grand
Copyright © 2015 Michael.Steindorfer@cwi.nl for TrieMap_5Bits

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
