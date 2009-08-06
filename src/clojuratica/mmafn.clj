(ns clojuratica.mmafn
  (:use [clojuratica.core]
        [clojuratica.parser]
        [clojuratica.lib])
  (:import [clojuratica CExpr]
           [com.wolfram.jlink Expr]))

(defnf mmafn-dispatch
  [args] []
  (let [assignments  (first args)
        expression   (second args)]
    (if-not (vector? assignments)
      (throw (Exception. (str "First argument to mmafn "
                              "must be a vector of assignments"))))
    (cond (string? expression)          :string
          (instance? Expr expression)   :expr
          (instance? CExpr expression)  :cexpr
          true (throw (Exception. (str "Second argument to mmafn must be "
                                       "string, Expr, or CExpr. You passed an object "
                                       "of class " (class expression)))))))

(defmulti mmafn mmafn-dispatch)

(defmethodf mmafn :string
  [[assignments s evaluate] _ passthrough-flags] []
  (let [kernel-link (evaluate :get-kernel-link)
        expr        (.getExpr (express s kernel-link))]
    (apply mmafn assignments expr evaluate passthrough-flags)))

(defmethodf mmafn :cexpr
  [[assignments cexpr evaluate] _ passthrough-flags] []
  (let [expr (.getExpr cexpr)]
    (apply mmafn assignments expr evaluate passthrough-flags)))

(defmethodf mmafn :expr
  [[assignments expr evaluate] flags passthrough_flags] [[:parse :no-parse]]
  (let [call (if (flags :parse) (comp parse evaluate) evaluate)
        head (.toString (.part expr 0))]
    (if-not (or (= "Set"        head)
                (= "SetDelayed" head)
                (= "Function"   head)
                (= "Symbol"     head))
      (throw (Exception. (str "mmafn must be passed a "
                              "string that contains a pure function "
                              "(head Function), a function definition "
                              "(head Set (=) or SetDelayed (:=)), or "
                              "a symbol (head Symbol)."))))
    (if (or (= "Function" head) (= "Symbol" head))
      (fn [& args]
        (let [expressed-args     (map (fn [x] (.getExpr (convert x))) args)
              expressed-arg-list (add-head "List" expressed-args)
              fn-call            (add-head "Apply" [expr expressed-arg-list])]
          (apply call assignments fn-call passthrough_flags)))
      (fn [& args]
        (let [lhs             (.part expr 1)
              expressed-args  (map (fn [x] (.getExpr (convert x))) args)
              name            (if (zero? (count (.args lhs)))
                                (.toString lhs)
                                (.toString (.head lhs)))
              assignments     (vec (concat assignments [name :undefined]))
              fn-call         (add-head name expressed-args)]
          (apply call assignments expr fn-call passthrough_flags))))))
