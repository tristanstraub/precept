(ns libx.tuplerules
    #?(:clj
       (:require [libx.macros :refer [rewrite-lhs insert-each-logical]]
                 [clara.macros :as cm]
                 [clara.rules.dsl :as dsl]
                 [clara.rules.compiler :as com]
                 [clara.rules :as cr]
                 [libx.util :as util])
       :cljs
       (:require-macros libx.tuplerules)))

(def rule-ids (atom {}))

(defn gen-rule-id [head]
  (let [existing (get @rule-ids head)]
    (if existing
     existing
     (let [rule-name (str "rule-" (util/guid))]
       (swap! rule-ids assoc head rule-name)
       rule-name))))

@rule-ids
(gen-rule-id '([-2 :foo bar]))

(str '([-3 :foo bar]))
(defn split-head-body
  [rule]
  (let [[head [sep & body]] (split-with #(not= ':- %) rule)]
    {:body body
     :head head}))


(defn head->rhs [head]
  (list 'do (list 'libx.util/insert! head)))

;(deflogical [?e :todo/visible :tag] :- [[_ :ui/visibility-filter :all]] [[?e :todo/title]])

(def macro-body '([?e :todo/visible :tag] :- [[_ :ui/visibility-filter :all]] [[?e :todo/title]]))

(split-head-body macro-body)

;; This technique borrowed from Prismatic's schema library (via clara).
#?(:clj
    (defn compiling-cljs?
      "Return true if we are currently generating cljs code.  Useful because cljx does not
             provide a hook for conditional macro expansion."
      []
      (boolean
        (when-let [n (find-ns 'cljs.analyzer)]
          (when-let [v (ns-resolve n '*cljs-file*)]
            ;; We perform this require only if we are compiling ClojureScript
            ;; so non-ClojureScript users do not need to pull in
            ;; that dependency.
            (require 'clara.macros)
            (require 'libx.macros)
            @v)))))

#?(:clj
   (defmacro def-tuple-session
     "Contains defaults for Clara's :fact-type-fn and :ancestors-fn"
     [name & sources-and-options]
     (if (compiling-cljs?)
       `(libx.macros/def-tuple-session ~name ~@sources-and-options)
       (let [sources (take-while (complement keyword?) sources-and-options)
             options (mapcat identity
                       (merge {:fact-type-fn :a
                               :ancestors-fn '(fn [type] [:all])}
                         (apply hash-map (drop-while (complement keyword?) sources-and-options))))
             body (vector (into options sources))]
         `(def ~name (com/mk-session ~@body))))))

#?(:clj
   (defmacro def-tuple-rule
     [name & body]
     (if (com/compiling-cljs?)
       `(libx.macros/def-tuple-rule ~name ~@body)
       (let [doc             (if (string? (first body)) (first body) nil)
             body            (if doc (rest body) body)
             properties      (if (map? (first body)) (first body) nil)
             definition      (if properties (rest body) body)
             {:keys [lhs rhs]} (dsl/split-lhs-rhs definition)
             lhs-detuplified (reverse (into '() (rewrite-lhs lhs)))]
         (when-not rhs
           (throw (ex-info (str "Invalid rule " name ". No RHS (missing =>?).")
                    {})))
         `(def ~(vary-meta name assoc :rule true :doc doc)
            (cond-> ~(dsl/parse-rule* lhs-detuplified rhs properties {} (meta &form))
              ~name (assoc :name ~(str (clojure.core/name (ns-name *ns*)) "/" (clojure.core/name name)))
              ~doc (assoc :doc ~doc)))))))

#?(:clj
   (defmacro def-tuple-query
     [name & body]
     (if (com/compiling-cljs?)
       `(libx.macros/def-tuple-query ~name ~@body)
       (let [doc (if (string? (first body)) (first body) nil)
             binding (if doc (second body) (first body))
             definition (if doc (drop 2 body) (rest body))
             rw-lhs (reverse (into '() (rewrite-lhs definition)))]
         `(def ~(vary-meta name assoc :query true :doc doc)
            (cond-> ~(dsl/parse-query* binding rw-lhs {} (meta &form))
              ~name (assoc :name ~(str (clojure.core/name (ns-name *ns*)) "/" (clojure.core/name name)))
              ~doc (assoc :doc ~doc)))))))

#?(:clj
   (defmacro deflogical
     [& body]
     (if (compiling-cljs?)
       `(libx.macros/deflogical ~@body)
       (let [{:keys [body head]} (split-head-body body)
             properties nil
             doc nil
             name (symbol (gen-rule-id head))
             lhs (rewrite-lhs body)
             rhs (head->rhs head)]
         (println "LHS" lhs)
         `(def ~(vary-meta name assoc :rule true :doc doc)
            (cond-> ~(dsl/parse-rule* lhs rhs properties {} (meta &form))
              ~name (assoc :name ~(str (clojure.core/name (ns-name *ns*)) "/" (clojure.core/name name)))
              ~doc (assoc :doc ~doc)))))))
