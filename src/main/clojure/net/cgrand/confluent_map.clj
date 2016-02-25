(ns net.cgrand.confluent-map)

(def e "empty confluent map" net.cgrand.ConfluentHashMap/EMPTY)
(def champ "empty CHAMP map" net.cgrand.TrieMap_5Bits/EMPTY_MAP)

(defn merge3
  "3-way merge for maps. Onc conflict fix is called with five arguments:
   key, value in ancestor, value in fork-a, value in fork-b, not-found.
   When one of the value is not-found it means the entry is not present for
   the corresponding map."
  ([ancestor fork-a fork-b]
    (merge3 ancestor fork-a fork-b
      (fn [k vanc va vb not-found]
        (throw (ex-info "Conflict" {:k k :vanc vanc :va va :vb vb :not-found not-found})))))
  ([ancestor fork-a fork-b fix]
    (net.cgrand.ConfluentHashMap/merge ancestor fork-a fork-b fix)))

(defn merge2 [a b fix]
  (merge3 e a b (fn [k _ va vb not-found]
                  (fix k va vb not-found))))