(ns libx.schema-test
  (:require [clojure.test :refer [deftest testing is run-tests]]
            [clara.rules :as cr]
            [clara.rules.accumulators :as acc]
            [libx.core :refer [state schema-insert] :as core]
            [libx.tuplerules :refer [def-tuple-session def-tuple-query]]
            [libx.schema-fixture :refer [test-schema]]))


(def-tuple-query find-all-facts []
  [?facts <- (acc/all) :from [:all]])

(defn all-facts [session]
  (:?facts (first (cr/query session find-all-facts))))

(deftest schema-test
  (testing "Writing user-supplied schema to state atom"
    (let [txed-schema (:schema (core/init-schema test-schema))]
     (is (= (:schema @state) txed-schema))
     (is (= (keys (:schema @state)) (map :db/ident test-schema))
       "User schema in state should be keyed by ident")))
  (testing "Insert where user schema defines unique identity"
    (let [test-session @(def-tuple-session _test-session 'libx.schema-test)
          unique-identity-fact-1 [1 :done-count 1]
          unique-identity-fact-2 [2 :done-count 2]
          first-insert (cr/fire-rules (core/schema-insert test-session unique-identity-fact-1))
          second-insert (cr/fire-rules (core/schema-insert first-insert unique-identity-fact-2))]
      (is (= (all-facts test-session) []))
      (is (= (all-facts first-insert) [unique-identity-fact-1]))
      (is (= (all-facts second-insert) [unique-identity-fact-2]))))
  (testing "Insert where user schema defines unique value"
    (let [test-session @(def-tuple-session _test-session 'libx.schema-test)
          unique-identity-fact-1 [1 :done-count 1]
          unique-identity-fact-2 [2 :done-count 2]
          first-insert (cr/fire-rules (core/schema-insert test-session unique-identity-fact-1))
          second-insert (cr/fire-rules (core/schema-insert first-insert unique-identity-fact-2))]
      (is (= (all-facts test-session) []))
      (is (= (all-facts first-insert) [unique-identity-fact-1]))
      (is (= (all-facts second-insert) [unique-identity-fact-2])))))
(run-tests)
