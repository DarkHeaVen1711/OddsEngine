# OddsEngine — Mathematical Methodology

Mathematical derivations and algorithms implemented in the C++ statistical core.

## 1. Generalized Elo-for-N

For two participants $A$ and $B$, the expected outcome $E_A$ is modeled using a logistic curve:

$$E_A = \frac{1}{1 + 10^{(R_B - R_A - H)/400}}$$

where $H = 100.0$ represents the home-field advantage (applied if $A$ is playing at home).

The ratings update following:

$$R'_A = R_A + K \cdot (S_A - E_A)$$

where $S_A$ is the actual finish rank outcome (1.0 for win, 0.5 for draw, 0.0 for loss). The $K$-factor scales dynamically based on match experience:
- $K = 32.0$ for provisional entities (fewer than 30 matches played).
- $K = 16.0$ for established entities.

---

## 2. Glicko-2 Rating System

Glicko-2 updates player rating $r$, rating deviation $RD$, and volatility $\sigma$.
1. Convert ratings to the Glicko-2 scale:
   $$\mu = \frac{r - 1500}{173.7178}, \quad \phi = \frac{RD}{173.7178}$$
2. Volunteer update for volatility $\sigma'$ utilizes Glickman's iterative **Illinois solver** (regula falsi) to solve the root-finding equation:
   $$f(x) = \frac{e^x (\Delta^2 - \phi^2 - v - e^x)}{2(\phi^2 + v + e^x)^2} - \frac{x - \ln(\sigma^2)}{\tau^2} = 0$$
   with an iteration guard limit of 100.
3. Convert updated parameters back to the Glicko-1 scale:
   $$r' = 173.7178 \cdot \mu' + 1500, \quad RD' = 173.7178 \cdot \phi'$$

---

## 3. Dixon-Coles Bivariate Poisson Model

For football scorelines $(X, Y)$ representing goals scored by home and away teams respectively:

$$P(X=x, Y=y) = \tau_{\lambda_1, \lambda_2, \lambda_3}(x, y) \cdot \frac{\lambda_1^x e^{-\lambda_1}}{x!} \cdot \frac{\lambda_2^y e^{-\lambda_2}}{y!}$$

where $\lambda_1 = \alpha_{\text{home}} \cdot \beta_{\text{away}} \cdot \gamma$ (home attack vs away defense times home advantage) and $\lambda_2 = \alpha_{\text{away}} \cdot \beta_{\text{home}}$. The covariance correction term $\tau$ modifies low-scoring draw/win probabilities (0-0, 1-0, 0-1, 1-1).

Fit via Gradient Descent optimization to minimize negative log-likelihood with L2 parameter regularization and sum constraint $\sum \alpha_i = N$.

---

## 4. Plackett-Luce F1 Model

For race participant finishing sequences, the probability of a ranking $R = (r_1, r_2, \dots, r_M)$ is modeled as:

$$P(R) = \prod_{i=1}^M \frac{\theta_{r_i}}{\sum_{j=i}^M \theta_{r_j}}$$

where the entrant strength $\theta_i$ is a function of driver skill $\theta_D$ and constructor performance $\theta_C$, scaled by grid position penalty $e^{-\alpha \cdot G}$ and constructors DNF rates $\pi_C$. Fits are solved via gradient descent optimization.

---

## 5. Bayesian Updating Layer

Using conjugate priors to log sequential match records:
- **Beta-Bernoulli** (win/loss): Prior $P(p) \sim \text{Beta}(\alpha_0, \beta_0)$ initialized from point Elo expectations, updated to posterior $\text{Beta}(\alpha_0 + W, \beta_0 + L)$ to resolve 90% credible intervals.
- **Dirichlet-Multinomial** (win/draw/loss): Prior updated similarly using Dirichlet posteriors for 3-way sports.
