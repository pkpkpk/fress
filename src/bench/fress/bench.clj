(ns fress.bench)


(defmacro run-trial
  [bindings expr iterations]
  `(let ~bindings
     (let [start#   (~'now)
           ret#     (dotimes [_# ~iterations] ~expr)
           end#     (~'now)
           elapsed# (- end# start#)]
       {:elapsed elapsed#
        :iterations ~iterations})))

(defmacro benchmark
  [bindings expr trial-count iterations & {:keys [print-fn] :or {print-fn 'println}}]
  (let [trial-expr `(run-trial ~bindings ~expr ~iterations)]
    `(let [acc# (~'array)]
       (dotimes [i# 3] ~trial-expr) ;warm up
       (dotimes [i# ~trial-count] (.push acc# ~trial-expr))
       (let [trials# (vec acc#)
             times# (map :elapsed trials#)
             mean# (apply ~'goog.math.average times#)
             sig# (apply ~'goog.math.standardDeviation times#)]
         {:trials ~trial-count
          :iterations/trial (get (first trials#) :iterations)
          :mean mean#
          :sigma sig#
          :expr ~(pr-str expr)}))))
