(ns core.matrix
  (:use core.matrix.utils)
  (:require [core.matrix.impl default double-array ndarray persistent-vector wrappers])
  (:require [core.matrix.impl sequence]) ;; TODO: figure out if we want this?
  (:require [core.matrix.multimethods :as mm])
  (:require [core.matrix.protocols :as mp])
  (:require [core.matrix.implementations :as imp])
  (:require [core.matrix.impl.mathsops :as mops]))

;; ==================================================================================
;; core.matrix API namespace
;;
;; This is the public API for core.matrix
;;
;; General handling of operations is as follows:
;; 
;; 1. user calls public AI function defined in core.matrix
;; 2. core.matrix function delegates to a protocol for the appropriate function
;;    with protocols as defined in the core.matrix.protocols namespace. In most cases
;;    core.matrix will try to delagate as quickly as possible to the implementation.
;; 3. The underlying matrix implementation implements the protocol to handle the API
;;    function call
;; 4. It's up to the implementation to decide what to do then
;; 5. If the implementation does not understand one or more parameters, then it is
;;    expected to call the multimethod version in core.matrix.multimethods as this
;;    will allow an alternative implementation to be found via multiple dispatch
;;
;; ==================================================================================

(set! *warn-on-reflection* true)
(set! *unchecked-math* true)

;; =============================================================
;; matrix construction functions

(declare current-implementation)
(declare current-implementation-object)
(def ^:dynamic *matrix-implementation* :persistent-vector)

(defn matrix
  "Constructs a matrix from the given data.

   The data may be in one of the following forms:
   - Nested sequences, e.g. Clojure vectors
   - A valid existing matrix

   If implementation is not specified, uses the current matrix library as specified
   in *matrix-implementation*"
  ([data]
    (if-let [m (current-implementation-object)]
      (mp/construct-matrix m data)
      (error "No core.matrix implementation available")))
  ([implementation data]
    (mp/construct-matrix (imp/get-canonical-object implementation) data)))

(defn array
  "Constructs a new n-dimensional array from the given data.

   The data may be in one of the following forms:
   - Nested sequences, e.g. Clojure vectors
   - A valid existing array

   If implementation is not specified, uses the current matrix library as specified
   in *matrix-implementation*"
  ([data]
    (if-let [m (current-implementation-object)]
      (mp/construct-matrix m data)
      (error "No core.matrix implementation available")))
  ([implementation data]
    (mp/construct-matrix (imp/get-canonical-object implementation) data)))

(defn new-vector
  "Constructs a new zero-filled vector with the given length"
  ([length]
    (if-let [m (current-implementation-object)]
      (mp/new-vector m length)
      (error "No core.matrix implementation available")))
  ([length implementation]
    (mp/new-vector (imp/get-canonical-object implementation) length)))

(defn new-matrix
  "Constructs a new zero-filled matrix with the given dimensions"
  ([rows columns]
    (if-let [ik (current-implementation)]
      (mp/new-matrix (imp/get-canonical-object ik) rows columns)
      (error "No core.matrix implementation available"))))

(defn new-array
  "Creates a new array with the given dimensions. "
  ([length] (new-vector length))
  ([rows columns] (new-matrix rows columns))
  ([dim-1 dim-2 & more-dim]
    (if-let [ik (current-implementation)]
      (mp/new-matrix-nd (imp/get-canonical-object ik) (cons dim-1 (cons dim-2 more-dim)))
      (error "No core.matrix implementation available"))))

(defn row-matrix
  "Constucts a row matrix with the given values. The returned matrix is a 2D 1xN row matrix."
  ([data]
   (if-let [ik (current-implementation)]
      (mp/construct-matrix (imp/get-canonical-object ik) (vector data))
      (error "No core.matrix implementation available")))
  ([implementation data]
    (mp/construct-matrix (imp/get-canonical-object implementation) (vector data))))

(defn column-matrix
  "Constucts a column matrix with the given values. The returned matrix is a 2D Nx1 column matrix."
  ([data]
   (if-let [ik (current-implementation)]
      (mp/construct-matrix (imp/get-canonical-object ik) (map vector data))
      (error "No core.matrix implementation available")))
  ([implementation data]
    (mp/construct-matrix (imp/get-canonical-object implementation) (map vector data))))

(defn identity-matrix
  "Constructs a 2D identity matrix with the given number or rows"
  ([dims]
    (mp/identity-matrix (current-implementation-object) dims))
  ([implementation dims]
    (mp/identity-matrix (imp/get-canonical-object implementation) dims)))

(defn diagonal-matrix
  "Constructs a 2D diagonal matrix with the given values on the main diagonal.
   diagonal-values may be a vector or any Clojure sequence of values."
  ([diagonal-values]
    (mp/diagonal-matrix (current-implementation-object) diagonal-values))
  ([implementation diagonal-values]
    (mp/diagonal-matrix (imp/get-canonical-object implementation) diagonal-values)))

(defn compute-matrix
  "Creates a matrix with the specified shape, and each element specified by (f i j k...)
   Where i, j, k... are the index positions of each element in the matrix"
  ([shape f]
    (compute-matrix (current-implementation-object) shape f))
  ([implementation shape f]
    ;; TODO: switch to a protocol implementation
    (let [m (imp/get-canonical-object implementation)]
      (TODO)))) 

;; ======================================
;; Implementation details

(defn supports-dimensionality?
  "Returns true if the implementation for a given matrix supports a specific dimensionality, i.e.
   can create and manipulate matrices with the given number of dimensions"
  ([m dimension-count]
    (let [m (if (keyword? m) (imp/get-canonical-object m) m)]
      (mp/supports-dimensionality? m dimension-count))))

;; ======================================
;; matrix assignment and copying

(defn assign!
  "Assigns a value to a matrix.
   Returns the mutated matrix"
  ([m a]
    (mp/assign! m a)
    m))

(defn clone
  "Constructs a clone of the matrix, using the same implementation. This function is intended to
   allow safe defensive copying of matrices / vectors.

   Guarantees that:
   1. Mutating the returned matrix will not modify any other matrix (defensive copy)
   2. The return matrix will be mutable, if the implementation supports mutable matrices.

   A matrix implementation which only provides immutable matrices may safely return the same matrix."
  ([m]
    (mp/clone m)))

(defn to-nested-vectors
  "Converts an array to nested vectors.
   The depth of nesting is equal to the dimensionality of the array."
  ([m]
    (mp/convert-to-nested-vectors m)))

;; ==============================
;; Matrix predicates and querying

(defn array?
  "Returns true if the parameter is an N-dimensional array, for any N>=1"
  ([m]
    (satisfies? mp/PImplementation m)))

(defn matrix?
  "Returns true if parameter is a valid matrix (dimensionality == 2)"
  ([m]
    (== (mp/dimensionality m) 2)))

(defn vec?
  "Returns true if the parameter is a vector"
  ([m]
    (mp/is-vector? m)))

(defn scalar?
  "Returns true if the parameter is a scalar (i.e. zero dimensionality, acceptable as matrix value)."
  ([m]
    (mp/is-scalar? m)))

(defn element-type
  "Returns the class of elements in the array."
  ([m]
    (mp/element-type m))) 

(defn dimensionality
;; TODO: alternative names to consider: order, tensor-rank?
  "Returns the dimensionality (number of array dimensions) of a matrix / array"
  ([m]
    (mp/dimensionality m)))

(defn row-count
  "Returns the number of rows in a matrix (must be 1D or more)"
  ([m]
    (mp/dimension-count m 0)))

(defn column-count
  "Returns the number of columns in a matrix (must be 2D or more)"
  ([m]
    (mp/dimension-count m 1)))

(defn dimension-count
  "Returns the size of the specified dimension in a matrix."
  ([m dim]
    (mp/dimension-count m dim)))

(defn square?
  "Returns true if matrix is square (2D with same number of rows and columns)"
  ([m]
    (and
      (== 2 (mp/dimensionality m))
      (== (mp/dimension-count m 0) (mp/dimension-count m 1)))))

(defn row-matrix?
  "Returns true if a matrix is a row-matrix (i.e. has exactly one row)"
  ([m]
    (and (== (mp/dimensionality m) 2)
         (== 1 (mp/dimension-count m 0)))))

(defn column-matrix?
  "Returns true if a matrix is a column-matrix (i.e. has exactly one column)"
  ([m]
    (and (== (mp/dimensionality m) 2)
         (== 1 (mp/dimension-count m 1)))))

(defn shape
  "Returns the shape of a matrix, i.e. the dimension sizes for all dimensions.

   Result may be a sequence or Java array, to allow implemenations flexibility to return
   their own internal representation of matrix shape.

   You are guaranteed however that you can call `seq` on this to get a sequence of dimension sizes."
  ([m]
    (mp/get-shape m)))

(defn mutable?
  "Returns true if the matrix is mutable, i.e. supports setting of values"
  ([m]
    (and (satisfies? mp/PIndexedSetting m) (mp/is-mutable? m))))


(defn- broadcast-shape*
  ([a b]
    (cond 
      (nil? a) (or b '())
      (nil? b) a
      (== 1 (first a)) (broadcast-shape* (first b) (next a) (next b))
      (== 1 (first b)) (broadcast-shape* (first a) (next a) (next b))
      (== (first a) (first b)) (broadcast-shape* (first a) (next a) (next b))
      :else nil))
  ([prefix a b]
    (if (or a b)
      (let [r (broadcast-shape* a b)]
        (if r (cons prefix r) nil))
      (cons prefix nil))))

(defn broadcast-shape 
  "Returns the smallest compatible shape that shapes a and b can both broadcast to.
   Returns nil if this is not possible (i.e. the shapes are incompatible). 
   Returns an empty list if both shape sequences are empty (i.e. represent scalars)" 
  ([a b]
    (let [a (seq (reverse a))
          b (seq (reverse b))
          r (broadcast-shape* a b)]
      (if r (reverse r) nil)))) 

;; =======================================
;; Conversions

(defn to-double-array 
   "Returns a double array containing the values of m in row-major order. 
    If want-copy is true, will guarantee a new double array (defensive copy).
    If want-copy is false, will return the internal array used by m, or nil if not supported
    by the implementation.
    If want copy is not sepcified, will return either a copy or the internal array"
   ([m]
     (mp/to-double-array m))
   ([m want-copy?]
     (let [arr (mp/as-double-array m)]
       (if want-copy?
         (if arr (copy-double-array arr) (mp/to-double-array m))
         arr))))

;; =======================================
;; matrix access

(defn mget
  "Gets a scalar value from a matrix at a specified position. Supports any number of matrix dimensions."
  ([m]
    (mp/get-0d m))
  ([m x]
    (mp/get-1d m x))
  ([m x y]
    (mp/get-2d m x y))
  ([m x y & more]
    (mp/get-nd m (cons x (cons y more)))))

(defn mset!
  "Sets a scalar value in a matrix at a specified position. Supports any number of matrix dimensions.
   Will throw an error if the matrix is not mutable."
  ([m v]
    (if (mp/is-scalar? m)
      (error "Can't set a scalar value!")
      (error "Can't mset! without indexes on array of dimensionality: " (dimensionality m))))
  ([m x v]
    (mp/set-1d m x v))
  ([m x y v]
    (mp/set-2d m x y v))
  ([m x y z & more]
    (mp/set-nd m (cons x (cons y (cons z (butlast more)))) (last more))))

(defn get-row
  "Gets a row of a 2D matrix.
   May return a mutable view if supported by the implementation."
  ([m x]
    (mp/get-row m x)))

(defn get-column
  "Gets a column of a 2D matrix.
   May return a mutable view if supported by the implementation."
  ([m y]
    (mp/get-column m y)))

(defn coerce
  "Coerces param to a format usable by a specific matrix implementation.
   If param is already in a format deemed usable by the implementation, returns it unchanged."
  ([m param]
    (or
      (mp/coerce-param m param)
      (mp/coerce-param m (mp/convert-to-nested-vectors param)))))

;; =====================================
;; matrix slicing and views

(defn sub-matrix
  "Gets a view of a submatrix, for a set of index-ranges.
   Index ranges should be  a sequence of [start, length] pairs.
   Index ranges can be nil (gets the whole range) "
  ([m index-ranges]
    (TODO)))

(defn sub-vector
  "Gets a view of part of a vector. The view maintains a reference to the original,
   so can be used to modify the original vector if it is mutable."
  ([m start length]
    (TODO)))

(defn slice
  "Gets a view of a slice of a matrix along a specific dimension.
   The returned matrix will have one less dimension.
   Slicing a 1D vector will return a scalar.

   Slicing on the first dimension (dimension 0) is likely to perform better
   for many matrix implementations."
  ([m index]
    (mp/get-slice m 0 index))
  ([m dimension index]
    (mp/get-slice m dimension index)))

(defn slices
  "Gets a lazy sequence of slices of a matrix. If dimension is supplied, slices along a given dimension,
   otherwise slices along the first dimension."
  ([m]
    (mp/get-major-slice-seq m))
  ([m dimension]
    (map #(mp/get-slice m dimension %) (range (mp/dimension-count m dimension)))))

(defn main-diagonal
  "Returns the main diagonal of a matrix or general array, as a vector"
  ([m]
    (mp/main-diagonal m)))

(defn rotate
  "Rotates an array along specified dimensions"
  ([m dimension shift-amount]
    (TODO))
  ([m [shifts]]
    (TODO))) 


;; ====================================
;; structural change operations

(defn broadcast 
  "Broadcasts a matrix to a specified shape"
  ([m shape]
    (mp/broadcast m shape)))

(defn transpose
  "Transposes a 2D matrix"
  ([m]
    (mp/transpose m)))

(defn transpose!
  "Transposes a square 2D matrix in-place"
  ([m]
    ;; TODO: implement with a proper protocol
    (assign! m (transpose m))))

(defn reshape 
  "Changes the shape of a matrix to the specified new shape. shape can be any sequence of dimension sizes.
   Preserves the row-major order of matrix elements."
  ([m shape]
    (mp/reshape m shape))) 

;; ======================================
;; matrix comparisons

(defn equals
  "Returns true if two matrices are numerically equal."
  ([a b]
    (mp/matrix-equals a b))
  ([a b epsilon]
    (TODO)))

;; ======================================
;; matrix maths / operations

(defn mul
  "Performs matrix multiplication with matrices, vectors or scalars"
  ([a] a)
  ([a b]
    (cond
      (scalar? b) (if (scalar? a) (* a b) (mp/scale a b))
      (scalar? a) (mp/pre-scale b a)
      :else (mp/matrix-multiply a b)))
  ([a b & more]
    (reduce mul (mul a b) more)))

(defn emul
  "Performs element-wise matrix multiplication. Matrices must be same size."
  ([a] a)
  ([a b]
    (mp/element-multiply a b))
  ([a b & more]
    (reduce mp/element-multiply (mp/element-multiply a b) more)))

(defn emul!
  "Performs in-place element-wise matrix multiplication."
  ([a] a)
  ([a b]
    (TODO))
  ([a b & more]
    (TODO))) 

(defn transform
  "Transforms a given vector, returning a new vector"
  ([m v] (mp/vector-transform m v)))

(defn transform!
  "Transforms a given vector in place"
  ([m v] (mp/vector-transform! m v)))

(defn add
  "Performs element-wise matrix addition on one or more matrices."
  ([a] a)
  ([a b]
    (mp/matrix-add a b))
  ([a b & more]
    (reduce mp/matrix-add (mp/matrix-add a b) more)))

(defn sub
  "Performs element-wise matrix subtraction on one or more matrices."
  ([a] a)
  ([a b]
    (mp/matrix-sub a b))
  ([a b & more]
    (reduce mp/matrix-sub (mp/matrix-sub a b) more)))

(defn scale
  "Scales a matrix by a scalar factor"
  ([m factor]
    (mp/scale m factor)))

(defn scale!
  "Scales a matrix by a scalar factor (in place)"
  ([m factor]
    (mp/scale! m factor)))

(defn normalise
  "Normalises a matrix (scales to unit length)"
  ([m]
    (mp/normalise m)))

(defn normalise!
  "Normalises a matrix in-place (scales to unit length).
   Returns the modified vector."
  ([m]
    (mp/normalise! m)))

(defn dot
  "Computes the dot product (inner product) of two vectors"
  ([a b]
    (mp/vector-dot a b)))

(defn det
  "Calculates the determinant of a matrix"
  ([a]
    (mp/determinant a)))


(defn trace
  "Calculates the trace of a matrix (sum of elements on main diagonal)"
  ([a]
    (mp/trace a)))

(defn length
  "Calculates the length (magnitude) of a vector"
  ([m]
    (mp/length m)))

(defn length-squared
  "Calculates the squared length (squared magnitude) of a vector"
  ([m]
     (mp/length-squared m)))

(defn esum
  "Calculates the sum of all the elements"
  [m]
  (mp/element-sum m))

;; create all unary maths operators
(eval
  `(do ~@(map (fn [[name func]]
           `(defn ~name
              ([~'m]
                (~(symbol "core.matrix.protocols" (str name)) ~'m)))) mops/maths-ops)
     ~@(map (fn [[name func]]
           `(defn ~(symbol (str name "!"))
              ([~'m]
                (~(symbol "core.matrix.protocols" (str name "!")) ~'m)))) mops/maths-ops))
       )

;; ====================================
;; functional operations

(defn ecount
  "Returns the total count of elements in an array"
  ([m]
    (cond
      (array? m) (reduce * 1 (mp/get-shape m))
      :else (count m))))

(defn eseq
  "Returns all elements of an array as a sequence in row-major order"
  ([m]
    (mp/element-seq m)))

(defn ereduce
  "Element-wise reduce on all elements of an array."
  ([f m]
    (mp/element-reduce m f))
  ([f init m]
    (mp/element-reduce m f init)))

(defn emap
  "Element-wise map over all elements of one or more arrays.
   Returns a new array of the same type and shape."
  ([f m]
    (mp/element-map m f))
  ([f m a]
    (mp/element-map m f a))
  ([f m a & more]
    (mp/element-map m f a more)))

(defn e=
  "Returns true if all array elements are equal (using Object.equals).
   WARNING: a java.lang.Long does not equal a lava.lang.Double.
   Use 'equals' or 'e==' instead if you want numerical equality."
  ([f m1]
    true)
  ([f m1 m2]
    (every? true? (map = (eseq m1) (eseq m2))))
  ([f m1 m2 & more]
    (reduce e= (e= m1 m2) more))) 

(defn e==
  "Returns true if all array elements are numerically equal (using ==)"
  ([f m1]
    true)
  ([f m1 m2]
    (equals m1 m2))
  ([f m1 m2 & more]
    (reduce equals (equals m1 m2) more))) 

(defn emap!
  "Element-wise map over all elements of one or more arrays.
   Performs in-place modification of the first array argument."
  ([f m]
    (mp/element-map! m f))
  ([f m a]
    (mp/element-map! m f a))
  ([f m a & more]
    (mp/element-map! m f a more)))

(defn index-seq-for-shape [sh]
  "Returns a sequence of all possible index vectors for a given shape, in row-major order"
  (let [gen (fn gen [prefix rem] 
              (if rem 
                (let [nrem (next rem)]
                  (mapcat #(gen (conj prefix %) nrem) (range (first rem))))
                (list prefix)))]
    (gen [] (seq sh)))) 

(defn index-seq [m]
  "Returns a sequence of all possible index vectors in a matrix, in row-major order"
  (index-seq-for-shape (shape m))) 


;; =========================================================
;; Implementation management functions

(defn current-implementation
  "Gets the currently active matrix implementation"
  ([] core.matrix/*matrix-implementation*))

(defn current-implementation-object
  "Gets the currently active matrix implementation"
  ([] (imp/get-canonical-object (current-implementation))))

(defn set-current-implementation
  "Sets the currently active matrix implementation"
  ([m]
    (alter-var-root (var core.matrix/*matrix-implementation*)
                    (fn [_] (imp/get-implementation-key m)))))
