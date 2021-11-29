(ns hitchhiker.tree.core-test
  (:refer-clojure :exclude [compare resolve])
  (:require [clojure.test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [hitchhiker.tree.core :refer :all]
            [clojure.core.async :refer [promise-chan] :as async]))

#_
(deftest reduce<-test
  (is (= 45 (<?? (reduce< (fn [res s]
                            (go-try (+ res s)))
                          0
                          (range 10))))))

(def <?? identity)

(deftest simple-read-only-behavior
  (testing "Basic searches"
    (let [data1 (data-node (->Config 3 5 2) (sorted-map 1 1 2 2 3 3 4 4 5 5))
          data2 (data-node (->Config 3 5 2) (sorted-map 6 6 7 7 8 8 9 9 10 10))
          root (->IndexNode [data1 data2] (promise-chan) [] (->Config 3 5 2))]
      (is (= (<?? (lookup-key root -10)) nil) "not found key")
      (is (= (<?? (lookup-key root 100)) nil) "not found key")
      (dotimes [i 10]
        (is (= (<?? (lookup-key root (inc i))) (inc i))))))
  (testing "Basic string key searches"
    (let [data1 (data-node (->Config 3 5 2) (sorted-map "1" 1 "10" 10 "2" 2 "3" 3 "4" 4))
          data2 (data-node (->Config 3 5 2) (sorted-map "5" 5 "6" 6 "7" 7 "8" 8 "9" 9))
          root (->IndexNode [data1 data2] (promise-chan) [] (->Config 3 5 2))]
      (is (= (<?? (lookup-key root "-10")) nil) "not found key")
      (is (= (<?? (lookup-key root "100")) nil) "not found key")
      (dotimes [i 10]
        (is (= (<?? (lookup-key root (str (inc i)))) (inc i))))))
  (testing "basic fwd iterator"
    (let [data1 (data-node (->Config 3 5 2) (sorted-map 1 1 2 2 3 3 4 4 5 5))
          data2 (data-node (->Config 3 5 2) (sorted-map 6 6 7 7 8 8 9 9 10 10))
          root (->IndexNode [data1 data2] (promise-chan) [] (->Config 3 5 2))]
      (is (= (map first (lookup-fwd-iter root 4)) (range 4 11)))
      (is (= (map first (lookup-fwd-iter root 0)) (range 1 11)))))
  (testing "index nodes identified as such"
    (let [data (data-node (->Config 3 5 2) (sorted-map 1 1))
          root (->IndexNode [data] (promise-chan) [] (->Config 3 5 2))]
      (is (index? root))
      (is (not (index? data))))))


(defn insert-helper
  [t k]
  (<?? (insert t k k)))

(def added-keys-appear-in-order
  (prop/for-all [v (gen/vector gen/int)]
                (let [sorted-set-order (into (sorted-set) v)
                      b-tree (reduce insert-helper
                                     (<?? (b-tree (->Config 3 3 2)))
                                     v)
                      b-tree-order (lookup-fwd-iter b-tree Integer/MIN_VALUE)]
                  (= (seq sorted-set-order) (seq (map first b-tree-order))))))

(defspec b-tree-sorts-uniques-random-int-vector
  1000
  added-keys-appear-in-order)

(defspec test-insert
  1000
  (prop/for-all [v (gen/vector gen/int)]
                (let [sorted-set-order (into (sorted-set) v)
                      b-tree (reduce insert-helper (<?? (b-tree (->Config 3 3 2))) v)
                      b-tree-order (lookup-fwd-iter b-tree Integer/MIN_VALUE)]
                  (= (seq sorted-set-order) (seq (map first b-tree-order))))))

(defspec test-insert-string
  1000
  (prop/for-all [v (gen/vector gen/string)]
                (let [sorted-set-order (into (sorted-set) v)
                      b-tree (reduce insert-helper (<?? (b-tree (->Config 3 3 2))) v)
                      b-tree-order (lookup-fwd-iter b-tree "")]
                  (= (seq sorted-set-order) (seq (map first b-tree-order))))))

(defspec test-delete2
  1000
  (prop/for-all [the-set (gen/vector-distinct gen/int)
                 num gen/nat]
                (let [set-a (sort the-set)
                      set-b (take num the-set)
                      b-tree (reduce insert-helper (<?? (b-tree (->Config 3 3 2))) set-a)
                      b-tree-without (reduce #(<?? (delete %1 %2)) b-tree set-b)
                      b-tree-order (lookup-fwd-iter b-tree-without Integer/MIN_VALUE)]
                  (= (seq (remove (set set-b) set-a)) (seq (map first b-tree-order))))))


(deftest insert-test
  (let [data1 (data-node (->Config 3 3 2) (sorted-map 1 "1" 2 "2" 3 "3" 4 "4"))
        root (->IndexNode [data1] (promise-chan) [] (->Config 3 3 2))]
    (is (= (map second (lookup-fwd-iter (<?? (insert root 3 "3")) -10)) ["1" "2" "3" "4"]))
    (are [x] (= (map second (lookup-fwd-iter (<?? (insert root x (str x))) -10)) (sort (map str (conj [1 2 3 4] x))))
         0
         2.5
         5))
  (let [data1 (data-node (->Config 3 3 2) (sorted-map 1 1 2 2 3 3 4 4 5 5))
        root (->IndexNode [data1] (promise-chan) [] (->Config 3 3 2))]
    (are [x y] (= (map first (lookup-fwd-iter (<?? (insert root x x)) y))
                  (drop-while
                    #(< % y)
                    (sort (conj [1 2 3 4 5] x))))
         0 3
         2.5 2
         5.5 3)))

(deftest simple-delete-tests
  (let [tree (reduce insert-helper (<?? (b-tree (->Config 3 3 2))) (range 5))]
    (is (= (map first (lookup-fwd-iter (<?? (delete tree 3)) 0)) [0 1 2 4])))
  (let [tree (reduce insert-helper (<?? (b-tree (->Config 3 3 2))) (range 10))]
    (is (= (map first (lookup-fwd-iter (<?? (delete tree 0)) 0)) (map inc (range 9)))))
  (let [tree (reduce insert-helper (<?? (b-tree (->Config 3 3 2))) (range 6))]
    (is (= (map first (lookup-fwd-iter (<?? (delete tree 0)) 0)) (map inc (range 5))))))

(defn check-node-is-balanced
  "Given a node, checks that it's balanced.
   All children must have b to 2b-1 children.
   The intent is to feed this a root node, which will have 2 as the min"
  ([node]
   (let [{:keys [index-b data-b]} (:cfg node)]
     (or
       ;; First case: it's just a datanode
       (and (data-node? node)
            (< (count (:children node)) (* 2 data-b)))
       ;; Second case: it's an index
       (let [acc (atom [])] ; acc is for ensuring all leaves are the same depth
         (and (check-node-is-balanced node index-b acc 2 0)
              (apply = @acc))))))
  ([{:keys [children] :as node} b acc min height]
   (if (<= min (count children) (dec (* 2 b))) ;between min (inc) & max (exc)
     (if-not (data-node? node)
       (every? #(check-node-is-balanced % b acc b (inc height)) children)
       (do
         (swap! acc conj height)
         true))
     false)))

(defspec test-balanced-after-many-inserts
  1000
  (prop/for-all [the-set (gen/vector (gen/no-shrink gen/int))]
                (let [b-tree (reduce insert-helper (<?? (b-tree (->Config 3 3 2))) the-set)]
                  (check-node-is-balanced b-tree))))

(defspec test-wider-balanced-after-many-inserts
  1000
  (prop/for-all [the-set (gen/vector (gen/no-shrink gen/int))]
                (let [b-tree (reduce insert-helper (<?? (b-tree (->Config 200 250 17))) the-set)]
                  (check-node-is-balanced b-tree))))

#_(require '[criterium.core :refer (quick-bench)])
;;TODO this is very slow...why is it so much slower?
;;Probably insane overuse of catvec/subvec...
;;Need to profile!
#_(quick-bench (reduce insert (b-tree (->Config 1000 1100 20)) (range 10000)))
#_(quick-bench (into []  (range 10000)))

(defn mixed-op-seq
  "Returns a property that ensures trees produced by a sequence of adds and deletes
   in the given ratio, with universe-size distinct values"
  [add-vs-del-ratio universe-size num-ops]
  (let [add-freq (long (* 1000 add-vs-del-ratio))
        del-freq (long (* 1000 (- 1 add-vs-del-ratio)))]
    (prop/for-all [ops (gen/vector (gen/frequency
                                     [[add-freq (gen/tuple (gen/return :add)
                                                           (gen/no-shrink gen/int))]
                                      [del-freq (gen/tuple (gen/return :del)
                                                           (gen/no-shrink gen/int))]])
                                   num-ops)]
                  (let [b-tree (reduce (fn [t [op x]]
                                         (let [x-reduced (mod x universe-size)]
                                           (condp = op
                                             :add (insert-helper t x-reduced)
                                             :del (<?? (delete t x-reduced)))))
                                       (<?? (b-tree (->Config 3 3 2)))
                                       ops)]
  ;                  (println ops)
                    (check-node-is-balanced b-tree)))))

(defspec test-few-keys-many-ops
  50
  (mixed-op-seq 0.5 250 5000))

(defspec test-many-keys-bigger-trees
  1000
  (mixed-op-seq 0.8 1000 1000))

(defspec test-sparse-ops
  1000
  (mixed-op-seq 0.7 100000 1000))

(comment
  (time (tc/quick-check 1 (mixed-op-seq 0.5 100 1000))))

(defspec test-flush
  1000
  (prop/for-all [v (gen/vector gen/int)]
                (let [sorted-set-order (into (sorted-set) v)
                      b-tree (reduce insert-helper (<?? (b-tree (->Config 3 3 2))) v)
                      b-tree-order (map first (lookup-fwd-iter b-tree Integer/MIN_VALUE))
                      flushed-tree (:tree (<?? (flush-tree b-tree (->TestingBackend))))
                      flushed-tree-order (map first (lookup-fwd-iter flushed-tree Integer/MIN_VALUE))]
                  (= (seq sorted-set-order)
                     (seq b-tree-order)
                     (seq flushed-tree-order)))))
;;TODO should test that flushing can be interleaved without races
