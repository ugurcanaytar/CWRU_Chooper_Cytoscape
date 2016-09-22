%  Convert your Binary-Symmetric Undirected Dataset to Adjacency Matrix
%  Matrix to be used must be column-normalized.

Data = load('Email-Enron.mat');

% For datasets from Stanford SNAP, use: f = Data.Problem.A;

f = Data.normalizedNetwork;
deg = sum(f,1);
zeros = (deg == 0);
deg(zeros) = 1;
deg = 1./deg;
normalizedNetwork = bsxfun(@times, f, deg);
normalizedNetwork = sparse(normalizedNetwork);
[row, col, v] = find(normalizedNetwork);
dlmwrite('row.txt', row);
dlmwrite('col.txt', col);
dlmwrite('val.txt', v);

system('paste -d\  row.txt col.txt val.txt > adjMat.txt');
system('paste -d\  row.txt col.txt > forCytoscape.txt');
